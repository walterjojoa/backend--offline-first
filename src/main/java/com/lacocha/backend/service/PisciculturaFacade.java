package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lacocha.backend.dto.Auth.LoginRequest;
import com.lacocha.backend.dto.Auth.PasswordChange;
import com.lacocha.backend.dto.Auth.TemporaryPassword;
import com.lacocha.backend.dto.Auth.UserUpdate;
import com.lacocha.backend.dto.Auth.RegisterRequest;
import com.lacocha.backend.dto.Auth.SessionResponse;
import com.lacocha.backend.dto.Auth.UserResponse;
import com.lacocha.backend.dto.Catalog.BatchCreate;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.dto.Catalog.BatchUpdate;
import com.lacocha.backend.dto.Catalog.PondCreate;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.PondUpdate;
import com.lacocha.backend.dto.Devices.DeviceResponse;
import com.lacocha.backend.dto.Devices.DeviceUpdate;
import com.lacocha.backend.dto.Devices.SyncLogResponse;
import com.lacocha.backend.dto.Forecasts.TemperatureReading;
import com.lacocha.backend.dto.Forecasts.WaterForecast;
import com.lacocha.backend.dto.Forecasts.WaterForecastRequest;
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
import com.lacocha.backend.model.ServerClock;

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

    private final AuthService auth;
    private final UserService people;
    private final CatalogService catalog;
    private final QueryService queries;
    private final SyncService sync;
    private final DeviceService devices;
    private final RuleParameters parameters;
    private final AiClient ai;

    public PisciculturaFacade(AuthService auth, UserService people, CatalogService catalog,
            QueryService queries, SyncService sync, DeviceService devices, RuleParameters parameters,
            AiClient ai) {
        this.auth = auth;
        this.people = people;
        this.catalog = catalog;
        this.queries = queries;
        this.sync = sync;
        this.devices = devices;
        this.parameters = parameters;
        this.ai = ai;
    }

    // ---------- Who is using the app ----------

    public SessionResponse register(RegisterRequest data) {
        return auth.register(data);
    }

    public SessionResponse login(LoginRequest data) {
        return auth.login(data);
    }

    public void logout(String token) {
        auth.logout(token);
    }

    /** Who owns the token, for the app to show the name and to check the session is still alive. */
    public UserResponse me(String token) {
        return auth.me(token);
    }

    // ---------- The people of the farm, which only the producer manages ----------

    public List<UserResponse> listUsers(String token) {
        auth.requireProducer(token);
        return people.list();
    }

    public UserResponse updateUser(String token, UUID id, UserUpdate data) {
        return people.update(id, data, auth.requireProducer(token));
    }

    /** Hands over a temporary password for someone who forgot theirs. */
    public TemporaryPassword resetPassword(String token, UUID id) {
        return people.resetPassword(id, auth.requireProducer(token));
    }

    /** Changing your own password. This is also how a temporary one stops being temporary. */
    public void changePassword(String token, PasswordChange data) {
        people.changePassword(auth.requireAllowingTemporary(token), data);
    }

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

    /**
     * Water temperature for the coming days. The AI learns how this pond follows the air from its own
     * readings, so it gets the ones it can pair with air: Open-Meteo gives back the last 92 days.
     */
    public WaterForecast waterForecast(UUID pondId, int days) {
        Instant since = ServerClock.now().minus(Duration.ofDays(92));
        List<TemperatureReading> readings = queries.pondReadings(pondId, since, null, 5000).stream()
                .filter(r -> r.tempC() != null)
                .map(r -> new TemperatureReading(r.recordedAt(), r.tempC()))
                .toList();
        if (readings.isEmpty()) {
            // The AI rejects an empty list; this is the same answer it gives for too few readings
            return new WaterForecast(List.of(), null, null, 0,
                    "Faltan lecturas: este estanque todavía no tiene lecturas de temperatura");
        }
        return ai.waterForecast(new WaterForecastRequest(readings, Math.max(1, Math.min(days, 7))));
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

    public AlertResponse attendAlert(UUID id, String who) {
        return queries.attendAlert(id, who);
    }

    // ---------- Synchronization with the phones ----------

    public PushResponse push(PushRequest request) {
        return sync.push(request);
    }

    public PullResponse pull(Instant since) {
        return sync.pull(since);
    }

    /**
     * Devices and their log are the producer's business: deactivating a lost phone decides whose
     * data still counts, and that is not something a caretaker should be able to do.
     */
    public List<DeviceResponse> listDevices(String token) {
        auth.requireProducer(token);
        return devices.list();
    }

    public DeviceResponse updateDevice(String token, String id, DeviceUpdate data) {
        auth.requireProducer(token);
        return devices.update(id, data);
    }

    public List<SyncLogResponse> syncLog(String token, String deviceId) {
        auth.requireProducer(token);
        return devices.syncLog(deviceId);
    }

    // ---------- Parameters of the expert system ----------

    public ParametersResponse parameters() {
        return parameters.response();
    }
}
