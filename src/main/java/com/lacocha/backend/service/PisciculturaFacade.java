package com.lacocha.backend.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.lacocha.backend.dto.Ai.BatchProjection;
import com.lacocha.backend.dto.Ai.FindingResponse;
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

    private final AuthService auth;
    private final UserService people;
    private final CatalogService catalog;
    private final QueryService queries;
    private final SyncService sync;
    private final DeviceService devices;
    private final RuleParameters parameters;
    private final AiAnalysis ai;
    private final EventStream stream;

    public PisciculturaFacade(AuthService auth, UserService people, CatalogService catalog,
            QueryService queries, SyncService sync, DeviceService devices, RuleParameters parameters,
            AiAnalysis ai, EventStream stream) {
        this.auth = auth;
        this.people = people;
        this.catalog = catalog;
        this.queries = queries;
        this.sync = sync;
        this.devices = devices;
        this.parameters = parameters;
        this.ai = ai;
        this.stream = stream;
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

    // ---------- What the AI service found ----------

    /**
     * Whether there is an AI service at all. The screens use it to decide whether to show the
     * findings section or leave it out, instead of showing an empty panel that looks broken.
     */
    public boolean aiAvailable() {
        return ai.available();
    }

    public List<FindingResponse> pendingFindings() {
        return ai.pending().stream().map(FindingResponse::from).toList();
    }

    public List<FindingResponse> pondFindings(UUID pondId) {
        return ai.ofPond(pondId).stream().map(FindingResponse::from).toList();
    }

    public List<FindingResponse> batchFindings(UUID batchId) {
        return ai.ofBatch(batchId).stream().map(FindingResponse::from).toList();
    }

    public FindingResponse attendFinding(UUID id, String who) {
        return FindingResponse.from(ai.attend(id, who));
    }

    /**
     * Where the batch is heading. Empty when there is no AI service to ask, which the screen shows
     * as "not available" and not as a projection of zero.
     */
    public Optional<BatchProjection> batchProjection(UUID batchId) {
        return ai.projectionOf(batchId);
    }

    // ---------- The event handler ----------

    /**
     * Opens the stream the screen listens on. Filtered by pond or batch when given, which is what
     * keeps a phone standing at one channel from being woken by every reading on the farm.
     */
    public SseEmitter openStream(UUID pondId, UUID batchId) {
        return stream.open(pondId, batchId);
    }

    public int listeningToStream() {
        return stream.listening();
    }
}
