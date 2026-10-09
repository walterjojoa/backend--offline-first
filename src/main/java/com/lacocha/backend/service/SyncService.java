package com.lacocha.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.dto.Catalog.BatchSync;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.PondSync;
import com.lacocha.backend.dto.Events;
import com.lacocha.backend.dto.Events.Input;
import com.lacocha.backend.dto.Events.WaterReadingInput;
import com.lacocha.backend.dto.JsonNames;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.dto.Sync.PullResponse;
import com.lacocha.backend.dto.Sync.PushRequest;
import com.lacocha.backend.dto.Sync.PushResponse;
import com.lacocha.backend.dto.Sync.Rejection;
import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.model.Pond;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.repository.AlertRepository;
import com.lacocha.backend.repository.BatchRepository;
import com.lacocha.backend.repository.PondRepository;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Offline-first synchronization with the caretaker's app.
 *
 * push: the phone uploads whatever is pending in its queue. It is idempotent: if the signal drops halfway
 *       and the phone retries, repeated events come back in "duplicados" and are not stored twice.
 * pull: the phone downloads catalog changes made from the panel or from another phone.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final EntityManager em;
    private final PondRepository ponds;
    private final BatchRepository batches;
    private final AlertRepository alerts;
    private final ObjectMapper mapper;
    private final Validator validator;

    public SyncService(EntityManager em, PondRepository ponds, BatchRepository batches,
            AlertRepository alerts, ObjectMapper mapper, Validator validator) {
        this.em = em;
        this.ponds = ponds;
        this.batches = batches;
        this.alerts = alerts;
        this.mapper = mapper;
        this.validator = validator;
    }

    /** Result that is filled in during the push. */
    private static final class PushResult {
        final List<UUID> accepted = new ArrayList<>();
        final List<UUID> duplicates = new ArrayList<>();
        final List<UUID> stale = new ArrayList<>();
        final List<Rejection> rejected = new ArrayList<>();
        int alertsCreated = 0;
    }

    /** A pond, batch or event that cannot be stored. It is rejected alone, the rest of the push goes on. */
    private static class InvalidItem extends Exception {
        InvalidItem(String message) {
            super(message);
        }
    }

    @Transactional
    public PushResponse push(PushRequest request) {
        Instant serverTime = ServerClock.now();
        PushResult result = new PushResult();

        for (JsonNode raw : orEmpty(request.ponds())) {
            PondSync p;
            try {
                p = parse(raw, PondSync.class);
            } catch (InvalidItem ex) {
                result.rejected.add(new Rejection(rawId(raw), ex.getMessage()));
                continue;
            }
            String error = Dates.deviceDateError(p.updatedAt());
            if (error != null) {
                result.rejected.add(new Rejection(p.id().toString(), "actualizado_en: " + error));
                continue;
            }
            Pond current = ponds.findById(p.id()).orElse(null);
            boolean isNew = current == null;
            if (isNew) {
                current = new Pond();
                current.setId(p.id());
            } else if (!p.updatedAt().toInstant().isAfter(current.getUpdatedAt())) {
                result.stale.add(p.id());
                continue;
            }
            current.setName(p.name());
            current.setType(p.type() != null ? p.type() : "estanque");
            current.setVolumeM3(p.volumeM3());
            current.setActive(p.active() == null || p.active());
            current.setUpdatedAt(p.updatedAt().toInstant());
            if (isNew) {
                em.persist(current);
            }
            result.accepted.add(p.id());
        }
        em.flush();

        for (JsonNode raw : orEmpty(request.batches())) {
            BatchSync b;
            try {
                b = parse(raw, BatchSync.class);
            } catch (InvalidItem ex) {
                result.rejected.add(new Rejection(rawId(raw), ex.getMessage()));
                continue;
            }
            String error = Dates.deviceDateError(b.updatedAt());
            if (error != null) {
                result.rejected.add(new Rejection(b.id().toString(), "actualizado_en: " + error));
                continue;
            }
            if (!ponds.existsById(b.pondId())) {
                result.rejected.add(new Rejection(b.id().toString(), "estanque_id: el estanque no existe"));
                continue;
            }
            Batch current = batches.findById(b.id()).orElse(null);
            boolean isNew = current == null;
            if (isNew) {
                current = new Batch();
                current.setId(b.id());
            } else if (!b.updatedAt().toInstant().isAfter(current.getUpdatedAt())) {
                result.stale.add(b.id());
                continue;
            }
            current.setPondId(b.pondId());
            current.setCode(b.code());
            current.setStockingDate(b.stockingDate());
            current.setInitialQuantity(b.initialQuantity());
            current.setInitialWeightG(b.initialWeightG());
            current.setStatus(b.status() != null ? b.status() : "activo");
            current.setUpdatedAt(b.updatedAt().toInstant());
            if (isNew) {
                em.persist(current);
            }
            result.accepted.add(b.id());
        }
        em.flush();

        Set<UUID> knownParents = new HashSet<>();
        for (JsonNode raw : orEmpty(request.events())) {
            Input event;
            try {
                event = readEvent(raw);
            } catch (InvalidItem ex) {
                result.rejected.add(new Rejection(rawId(raw), ex.getMessage()));
                continue;
            }

            if (!knownParents.contains(event.parentId())) {
                Class<?> parentType = event.parentIsPond() ? Pond.class : Batch.class;
                if (em.find(parentType, event.parentId()) == null) {
                    String field = event.parentIsPond() ? "estanque_id" : "lote_id";
                    result.rejected.add(new Rejection(event.id().toString(), field + ": no existe"));
                    continue;
                }
                knownParents.add(event.parentId());
            }

            Event entity = event.toEntity();
            if (em.find(entity.getClass(), event.id()) != null) {
                result.duplicates.add(event.id());
                continue;
            }
            entity.setId(event.id());
            entity.setDeviceId(request.deviceId());
            entity.setSource(event.source() != null ? event.source() : "manual");
            entity.setRecordedAt(event.recordedAt().toInstant());
            entity.setReceivedAt(serverTime);
            em.persist(entity);
            result.accepted.add(event.id());

            if (event instanceof WaterReadingInput reading) {
                result.alertsCreated += createAlerts(reading);
            }
        }

        log.info("push from {}: {} accepted, {} duplicates, {} stale, {} rejected, {} alerts",
                request.deviceId(), result.accepted.size(), result.duplicates.size(), result.stale.size(),
                result.rejected.size(), result.alertsCreated);
        return new PushResponse(result.accepted, result.duplicates, result.stale, result.rejected,
                result.alertsCreated, serverTime);
    }

    /** Runs the expert system on a reading and stores the alerts. Returns how many were created. */
    private int createAlerts(WaterReadingInput reading) {
        int created = 0;
        for (Rules.Result rule : Rules.evaluateReading(reading.tempC(), reading.ph(), reading.oxygenMgL())) {
            // The sensor measures every few seconds: if an identical alert is already pending, do not repeat it
            if (alerts.existsByPondIdAndVariableAndLevelAndAttendedFalse(
                    reading.pondId(), rule.variable(), rule.level())) {
                continue;
            }
            Alert alert = new Alert();
            alert.setPondId(reading.pondId());
            alert.setReadingId(reading.id());
            alert.setVariable(rule.variable());
            alert.setValue(rule.value());
            alert.setLevel(rule.level());
            alert.setMessage(rule.message());
            alert.setMeasuredAt(reading.recordedAt().toInstant());
            em.persist(alert);
            created++;
        }
        return created;
    }

    /** since is the servidor_en returned by the previous pull. Without it, everything is downloaded. */
    @Transactional(readOnly = true)
    public PullResponse pull(Instant since) {
        Instant serverTime = ServerClock.now();
        List<Pond> pondList = since == null ? ponds.findAll() : ponds.findByServerTimeAfter(since);
        List<Batch> batchList = since == null ? batches.findAll() : batches.findByServerTimeAfter(since);
        return new PullResponse(
                pondList.stream().map(PondResponse::from).toList(),
                batchList.stream().map(BatchResponse::from).toList(),
                alerts.findTop100ByAttendedFalseOrderByMeasuredAtDesc().stream().map(AlertResponse::from).toList(),
                serverTime);
    }

    private Input readEvent(JsonNode raw) throws InvalidItem {
        if (raw == null || !raw.isObject()) {
            throw new InvalidItem("el evento debe ser un objeto JSON");
        }
        Class<? extends Input> type = Events.TYPES.get(raw.path("tipo").asText(""));
        if (type == null) {
            throw new InvalidItem("tipo: debe ser uno de " + String.join(", ", Events.TYPES.keySet()));
        }

        Input event = parse(raw, type);
        String error = Dates.deviceDateError(event.recordedAt());
        if (error != null) {
            throw new InvalidItem("registrado_en: " + error);
        }
        error = event.extraError();
        if (error != null) {
            throw new InvalidItem(error);
        }
        return event;
    }

    /** JSON -> record, plus its annotations (@NotNull, @Size...). The error names the field as in the JSON. */
    private <T> T parse(JsonNode raw, Class<T> type) throws InvalidItem {
        if (raw == null || !raw.isObject()) {
            throw new InvalidItem("debe ser un objeto JSON");
        }
        T value;
        try {
            value = mapper.treeToValue(raw, type);
        } catch (JsonProcessingException ex) {
            throw new InvalidItem(fieldWithError(ex) + ": formato no válido");
        }

        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> v = violations.stream()
                    .min(Comparator.comparing(x -> x.getPropertyPath().toString()))
                    .get();
            throw new InvalidItem(JsonNames.of(type, v.getPropertyPath().toString()) + ": " + v.getMessage());
        }
        return value;
    }

    /** Jackson already reports the path with the JSON names. */
    private static String fieldWithError(JsonProcessingException ex) {
        if (ex instanceof MismatchedInputException m && !m.getPath().isEmpty()) {
            String field = m.getPath().get(m.getPath().size() - 1).getFieldName();
            if (field != null) {
                return field;
            }
        }
        return "dato";
    }

    private static String rawId(JsonNode raw) {
        return raw != null && raw.hasNonNull("id") ? raw.get("id").asText() : null;
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }
}
