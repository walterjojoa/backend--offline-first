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
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.PondSync;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.dto.Catalog.BatchSync;
import com.lacocha.backend.dto.Events;
import com.lacocha.backend.dto.Events.Input;
import com.lacocha.backend.dto.JsonNames;
import com.lacocha.backend.dto.Events.WaterReadingInput;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.dto.Sync.PullResponse;
import com.lacocha.backend.dto.Sync.PushRequest;
import com.lacocha.backend.dto.Sync.PushResponse;
import com.lacocha.backend.dto.Sync.Rejection;
import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Pond;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.repository.AlertRepository;
import com.lacocha.backend.repository.PondRepository;
import com.lacocha.backend.repository.BatchRepository;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Sincronización offline-first con la app del cuidador.
 *
 * push: el celular sube lo que tiene pendiente en su cola. Es idempotente: si la señal se cae a mitad
 *       del envío y el celular reintenta, los eventos repetidos salen en "duplicados" y no se guardan dos veces.
 * pull: el celular baja los cambios del catálogo hechos desde el panel o desde otro celular.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final EntityManager em;
    private final PondRepository ponds;
    private final BatchRepository batches;
    private final AlertRepository alertas;
    private final ObjectMapper mapper;
    private final Validator validator;

    public SyncService(EntityManager em, PondRepository ponds, BatchRepository batches,
            AlertRepository alertas, ObjectMapper mapper, Validator validator) {
        this.em = em;
        this.ponds = ponds;
        this.batches = batches;
        this.alertas = alertas;
        this.mapper = mapper;
        this.validator = validator;
    }

    /** Resultado que se va llenando durante el push. */
    private static final class Resultado {
        final List<UUID> accepted = new ArrayList<>();
        final List<UUID> duplicates = new ArrayList<>();
        final List<UUID> stale = new ArrayList<>();
        final List<Rejection> rejected = new ArrayList<>();
        int alertsCreated = 0;
    }

    private static class EventoInvalido extends Exception {
        EventoInvalido(String message) {
            super(message);
        }
    }

    @Transactional
    public PushResponse push(PushRequest peticion) {
        Instant serverTime = ServerClock.now();
        Resultado r = new Resultado();

        for (PondSync e : lista(peticion.ponds())) {
            String error = Fechas.errorFechaDispositivo(e.updatedAt());
            if (error != null) {
                r.rejected.add(new Rejection(e.id().toString(), "actualizado_en: " + error));
                continue;
            }
            Pond actual = ponds.findById(e.id()).orElse(null);
            boolean nuevo = actual == null;
            if (nuevo) {
                actual = new Pond();
                actual.setId(e.id());
            } else if (!e.updatedAt().toInstant().isAfter(actual.getUpdatedAt())) {
                r.stale.add(e.id());
                continue;
            }
            actual.setName(e.name());
            actual.setType(e.type() != null ? e.type() : "estanque");
            actual.setVolumeM3(e.volumeM3());
            actual.setActive(e.active() == null || e.active());
            actual.setUpdatedAt(e.updatedAt().toInstant());
            if (nuevo) {
                em.persist(actual);
            }
            r.accepted.add(e.id());
        }
        em.flush();

        for (BatchSync l : lista(peticion.batches())) {
            String error = Fechas.errorFechaDispositivo(l.updatedAt());
            if (error != null) {
                r.rejected.add(new Rejection(l.id().toString(), "actualizado_en: " + error));
                continue;
            }
            if (!ponds.existsById(l.pondId())) {
                r.rejected.add(new Rejection(l.id().toString(), "estanque_id: el estanque no existe"));
                continue;
            }
            Batch actual = batches.findById(l.id()).orElse(null);
            boolean nuevo = actual == null;
            if (nuevo) {
                actual = new Batch();
                actual.setId(l.id());
            } else if (!l.updatedAt().toInstant().isAfter(actual.getUpdatedAt())) {
                r.stale.add(l.id());
                continue;
            }
            actual.setPondId(l.pondId());
            actual.setCode(l.code());
            actual.setStockingDate(l.stockingDate());
            actual.setInitialQuantity(l.initialQuantity());
            actual.setInitialWeightG(l.initialWeightG());
            actual.setStatus(l.status() != null ? l.status() : "activo");
            actual.setUpdatedAt(l.updatedAt().toInstant());
            if (nuevo) {
                em.persist(actual);
            }
            r.accepted.add(l.id());
        }
        em.flush();

        Set<UUID> padresConocidos = new HashSet<>();
        for (JsonNode crudo : lista(peticion.events())) {
            Input evento;
            try {
                evento = leerEvento(crudo);
            } catch (EventoInvalido ex) {
                r.rejected.add(new Rejection(idCrudo(crudo), ex.getMessage()));
                continue;
            }

            if (!padresConocidos.contains(evento.parentId())) {
                Class<?> clasePadre = evento.parentIsPond() ? Pond.class : Batch.class;
                if (em.find(clasePadre, evento.parentId()) == null) {
                    String campo = evento.parentIsPond() ? "estanque_id" : "lote_id";
                    r.rejected.add(new Rejection(evento.id().toString(), campo + ": no existe"));
                    continue;
                }
                padresConocidos.add(evento.parentId());
            }

            Event entidad = evento.toEntity();
            if (em.find(entidad.getClass(), evento.id()) != null) {
                r.duplicates.add(evento.id());
                continue;
            }
            entidad.setId(evento.id());
            entidad.setDeviceId(peticion.deviceId());
            entidad.setSource(evento.source() != null ? evento.source() : "manual");
            entidad.setRecordedAt(evento.recordedAt().toInstant());
            entidad.setReceivedAt(serverTime);
            em.persist(entidad);
            r.accepted.add(evento.id());

            if (evento instanceof WaterReadingInput lectura) {
                for (Reglas.Resultado regla : Reglas.evaluarLectura(lectura.tempC(), lectura.ph(), lectura.oxygenMgL())) {
                    // El sensor mide cada pocos segundos: si ya hay una alerta pendiente igual, no se repite
                    if (alertas.existsByPondIdAndVariableAndLevelAndAttendedFalse(
                            lectura.pondId(), regla.variable(), regla.level())) {
                        continue;
                    }
                    Alert alerta = new Alert();
                    alerta.setPondId(lectura.pondId());
                    alerta.setReadingId(lectura.id());
                    alerta.setVariable(regla.variable());
                    alerta.setValue(regla.value());
                    alerta.setLevel(regla.level());
                    alerta.setMessage(regla.message());
                    alerta.setMeasuredAt(lectura.recordedAt().toInstant());
                    em.persist(alerta);
                    r.alertsCreated++;
                }
            }
        }

        log.info("push de {}: {} aceptados, {} duplicados, {} obsoletos, {} rechazados, {} alertas",
                peticion.deviceId(), r.accepted.size(), r.duplicates.size(), r.stale.size(),
                r.rejected.size(), r.alertsCreated);
        return new PushResponse(r.accepted, r.duplicates, r.stale, r.rejected, r.alertsCreated, serverTime);
    }

    /** desde es el servidor_en que devolvió el pull anterior. Sin él, baja todo. */
    @Transactional(readOnly = true)
    public PullResponse pull(Instant desde) {
        Instant serverTime = ServerClock.now();
        List<Pond> listaEstanques = desde == null ? ponds.findAll() : ponds.findByServerTimeAfter(desde);
        List<Batch> listaLotes = desde == null ? batches.findAll() : batches.findByServerTimeAfter(desde);
        return new PullResponse(
                listaEstanques.stream().map(PondResponse::from).toList(),
                listaLotes.stream().map(BatchResponse::from).toList(),
                alertas.findTop100ByAttendedFalseOrderByMeasuredAtDesc().stream().map(AlertResponse::from).toList(),
                serverTime);
    }

    private Input leerEvento(JsonNode crudo) throws EventoInvalido {
        if (crudo == null || !crudo.isObject()) {
            throw new EventoInvalido("el evento debe ser un objeto JSON");
        }
        Class<? extends Input> clase = Events.TYPES.get(crudo.path("tipo").asText(""));
        if (clase == null) {
            throw new EventoInvalido("tipo: debe ser uno de " + String.join(", ", Events.TYPES.keySet()));
        }

        Input evento;
        try {
            evento = mapper.treeToValue(crudo, clase);
        } catch (JsonProcessingException ex) {
            throw new EventoInvalido(campoConError(ex) + ": formato no válido");
        }

        Set<ConstraintViolation<Input>> violaciones = validator.validate(evento);
        if (!violaciones.isEmpty()) {
            ConstraintViolation<Input> v = violaciones.stream()
                    .min(Comparator.comparing(x -> x.getPropertyPath().toString()))
                    .get();
            throw new EventoInvalido(JsonNames.of(evento.getClass(), v.getPropertyPath().toString()) + ": " + v.getMessage());
        }

        String error = Fechas.errorFechaDispositivo(evento.recordedAt());
        if (error != null) {
            throw new EventoInvalido("registrado_en: " + error);
        }
        error = evento.extraError();
        if (error != null) {
            throw new EventoInvalido(error);
        }
        return evento;
    }

    private static String campoConError(JsonProcessingException ex) {
        if (ex instanceof MismatchedInputException m && !m.getPath().isEmpty()) {
            String campo = m.getPath().get(m.getPath().size() - 1).getFieldName();
            if (campo != null) {
                return campo;
            }
        }
        return "evento";
    }


    private static String idCrudo(JsonNode crudo) {
        return crudo != null && crudo.hasNonNull("id") ? crudo.get("id").asText() : null;
    }

    private static <T> List<T> lista(List<T> l) {
        return l != null ? l : List.of();
    }
}
