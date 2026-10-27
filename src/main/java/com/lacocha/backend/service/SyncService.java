package com.lacocha.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.dto.Catalog.BatchSync;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.PondSync;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.dto.Sync.PullResponse;
import com.lacocha.backend.dto.Sync.PushRequest;
import com.lacocha.backend.dto.Sync.PushResponse;
import com.lacocha.backend.dto.Sync.Rejection;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.Device;
import com.lacocha.backend.model.Pond;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.model.SyncLog;
import com.lacocha.backend.repository.AlertRepository;
import com.lacocha.backend.repository.BatchRepository;
import com.lacocha.backend.repository.DeviceRepository;
import com.lacocha.backend.repository.PondRepository;
import com.lacocha.backend.service.PushJsonAdapter.InvalidItem;

import jakarta.persistence.EntityManager;

/**
 * Offline-first synchronization with the app of the caretaker.
 *
 * push: the phone uploads whatever is pending in its queue. It is idempotent: if the signal drops
 *       halfway and the phone retries, repeated events come back in "duplicados" and are not stored
 *       twice. The catalog is merged here; the events are handed over to EventIngestion.
 * pull: the phone downloads catalog changes made from the panel or from another phone.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final EntityManager em;
    private final PondRepository ponds;
    private final BatchRepository batches;
    private final AlertRepository alerts;
    private final DeviceRepository devices;
    private final PushJsonAdapter adapter;
    private final EventIngestion events;

    public SyncService(EntityManager em, PondRepository ponds, BatchRepository batches,
            AlertRepository alerts, DeviceRepository devices, PushJsonAdapter adapter, EventIngestion events) {
        this.em = em;
        this.ponds = ponds;
        this.batches = batches;
        this.alerts = alerts;
        this.devices = devices;
        this.adapter = adapter;
        this.events = events;
    }

    /** Result that is filled in during the push. */
    private static final class PushResult {
        final List<UUID> accepted = new ArrayList<>();
        final List<UUID> duplicates = new ArrayList<>();
        final List<UUID> stale = new ArrayList<>();
        final List<Rejection> rejected = new ArrayList<>();
        int alertsCreated = 0;
    }

    @Transactional
    public PushResponse push(PushRequest request) {
        Instant serverTime = ServerClock.now();
        PushResult result = new PushResult();
        registerDevice(request.deviceId(), serverTime);

        mergePonds(orEmpty(request.ponds()), result);
        em.flush();
        mergeBatches(orEmpty(request.batches()), result);
        // Flushed before the events: one of them may point at a batch that arrived in this same push
        em.flush();

        EventIngestion.Outcome outcome = events.ingest(orEmpty(request.events()), request.deviceId(), serverTime);
        result.accepted.addAll(outcome.accepted());
        result.duplicates.addAll(outcome.duplicates());
        result.rejected.addAll(outcome.rejected());
        result.alertsCreated += outcome.alertsCreated();

        writeSyncLog(request.deviceId(), result, serverTime);
        log.info("push from {}: {} accepted, {} duplicates, {} stale, {} rejected, {} alerts",
                request.deviceId(), result.accepted.size(), result.duplicates.size(), result.stale.size(),
                result.rejected.size(), result.alertsCreated);
        return new PushResponse(result.accepted, result.duplicates, result.stale, result.rejected,
                result.alertsCreated, serverTime);
    }

    private void mergePonds(List<JsonNode> items, PushResult result) {
        for (JsonNode raw : items) {
            PondSync p;
            try {
                p = adapter.read(raw, PondSync.class);
            } catch (InvalidItem ex) {
                result.rejected.add(new Rejection(PushJsonAdapter.rawId(raw), ex.getMessage()));
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
            // Caught here and not by the database: a unique violation would roll back the whole push
            if (ponds.existsByNameAndIdNot(p.name(), p.id())) {
                result.rejected.add(new Rejection(p.id().toString(), "nombre: " + CatalogService.NAME_TAKEN));
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
    }

    private void mergeBatches(List<JsonNode> items, PushResult result) {
        for (JsonNode raw : items) {
            BatchSync b;
            try {
                b = adapter.read(raw, BatchSync.class);
            } catch (InvalidItem ex) {
                result.rejected.add(new Rejection(PushJsonAdapter.rawId(raw), ex.getMessage()));
                continue;
            }
            String error = Dates.deviceDateError(b.updatedAt());
            if (error != null) {
                result.rejected.add(new Rejection(b.id().toString(), "actualizado_en: " + error));
                continue;
            }
            error = CatalogService.closingError(b.stockingDate(), "cerrado".equals(b.status()) ? b.closingDate() : null);
            if (error != null) {
                result.rejected.add(new Rejection(b.id().toString(), error));
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
            if (batches.existsByPondIdAndCodeAndIdNot(b.pondId(), b.code(), b.id())) {
                result.rejected.add(new Rejection(b.id().toString(), "codigo: " + CatalogService.CODE_TAKEN));
                continue;
            }
            current.setPondId(b.pondId());
            current.setCode(b.code());
            current.setStockingDate(b.stockingDate());
            current.setInitialQuantity(b.initialQuantity());
            current.setInitialWeightG(b.initialWeightG());
            current.setStatus(b.status() != null ? b.status() : "activo");
            // A phone that does not know about the closing date sends none: the one already saved is kept
            if (b.closingDate() != null) {
                current.setClosingDate(b.closingDate());
            }
            CatalogService.adjustClosing(current);
            current.setUpdatedAt(b.updatedAt().toInstant());
            if (isNew) {
                em.persist(current);
            }
            result.accepted.add(b.id());
        }
    }

    /**
     * Records the phone (or ESP32 node) that is syncing and when it was last seen. It goes before the
     * events because dispositivo_id is a foreign key: the device row must exist before they are saved.
     */
    private void registerDevice(String id, Instant now) {
        Device device = devices.findById(id).orElse(null);
        if (device == null) {
            device = new Device();
            device.setId(id);
            device.setFirstSeenAt(now);
            device.setLastSeenAt(now);
            em.persist(device);
        } else if (!device.isActive()) {
            // A lost or stolen phone must not keep writing: the whole push is refused
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El dispositivo " + id + " está dado de baja");
        } else {
            device.setLastSeenAt(now);
        }
        em.flush();
    }

    /**
     * Keeps on the server the counts the phone receives. Inside the push transaction on purpose:
     * if the push rolls back, it must not look as if it had arrived.
     */
    private void writeSyncLog(String deviceId, PushResult result, Instant serverTime) {
        SyncLog entry = new SyncLog();
        entry.setDeviceId(deviceId);
        entry.setAccepted(result.accepted.size());
        entry.setDuplicates(result.duplicates.size());
        entry.setStale(result.stale.size());
        entry.setRejected(result.rejected.size());
        entry.setAlertsCreated(result.alertsCreated);
        entry.setServerTime(serverTime);
        em.persist(entry);
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

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }
}
