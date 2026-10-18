package com.lacocha.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lacocha.backend.dto.Catalog.BatchCreate;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.dto.Catalog.BatchUpdate;
import com.lacocha.backend.dto.Catalog.PondCreate;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.PondUpdate;
import com.lacocha.backend.dto.Devices.DeviceResponse;
import com.lacocha.backend.dto.Devices.DeviceUpdate;
import com.lacocha.backend.dto.Devices.SyncLogResponse;
import com.lacocha.backend.dto.Parameters.ParametersResponse;
import com.lacocha.backend.dto.Queries.BatchSummary;
import com.lacocha.backend.dto.Queries.BiometryResponse;
import com.lacocha.backend.dto.Queries.DailyReadings;
import com.lacocha.backend.dto.Queries.FeedingResponse;
import com.lacocha.backend.dto.Queries.FryCountResponse;
import com.lacocha.backend.dto.Queries.MortalityResponse;
import com.lacocha.backend.dto.Queries.WaterReadingResponse;
import com.lacocha.backend.dto.Sync.AlertResponse;
import com.lacocha.backend.dto.Sync.PullResponse;
import com.lacocha.backend.dto.Sync.PushRequest;
import com.lacocha.backend.dto.Sync.PushResponse;

/**
 * The one door into the system for the HTTP layer.
 *
 * Behind it live the four subsystems the controllers used to reach into one by one: the catalog of
 * ponds and batches, the synchronization with the phones, the reading of the events the devices
 * uploaded, and the parameters of the expert system. A controller should only know its route, its
 * JSON and which use case to call; which service holds that use case, and whether it stays there
 * tomorrow, is none of its business.
 *
 * The rule, easy to check with a grep: a controller injects this facade and no service.
 */
@Service
public class PisciculturaFacade {
    private final CatalogService catalog;

    private final QueryService queries;

    private final SyncService sync;

    private final DeviceService devices;

    // ---------- Catalog: ponds and batches, editable from the panel and from any phone ----------

    public List<PondResponse> listPonds() {
        return catalog.listPonds();
    }

    public PondResponse getPond(UUID id) {
        return catalog.getPond(id);
    }

    public PondResponse createPond(PondCreate data) {
        return catalog.createPond(data);
    }

    public PondResponse updatePond(UUID id, PondUpdate data) {
        return catalog.updatePond(id, data);
    }

    public List<BatchResponse> listBatches(UUID pondId, String status) {
        return catalog.listBatches(pondId, status);
    }

    public BatchResponse getBatch(UUID id) {
        return catalog.getBatch(id);
    }

    public BatchResponse createBatch(BatchCreate data) {
        return catalog.createBatch(data);
    }

    public BatchResponse updateBatch(UUID id, BatchUpdate data) {
        return catalog.updateBatch(id, data);
    }

    // ---------- Reading the events the devices uploaded ----------

    public List<WaterReadingResponse> pondReadings(UUID pondId, Instant since, Instant until, int limit) {
        return queries.pondReadings(pondId, since, until, limit);
    }

    public List<DailyReadings> dailyReadings(UUID pondId, int days) {
        return queries.dailyReadings(pondId, days);
    }

    public List<FryCountResponse> fryCounts(UUID batchId, int limit) {
        return queries.fryCounts(batchId, limit);
    }

    public List<MortalityResponse> mortalities(UUID batchId, int limit) {
        return queries.mortalities(batchId, limit);
    }

    public List<FeedingResponse> feedings(UUID batchId, int limit) {
        return queries.feedings(batchId, limit);
    }

    public List<BiometryResponse> biometries(UUID batchId, int limit) {
        return queries.biometries(batchId, limit);
    }

    /** What the expert system works out for one batch from its events. */
    public BatchSummary batchSummary(UUID batchId) {
        return queries.batchSummary(batchId);
    }

    public List<AlertResponse> listAlerts(boolean pendingOnly, UUID pondId, int limit) {
        return queries.listAlerts(pendingOnly, pondId, limit);
    }
}
