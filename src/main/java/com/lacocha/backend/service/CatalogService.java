package com.lacocha.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.dto.Catalog.BatchCreate;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.dto.Catalog.BatchUpdate;
import com.lacocha.backend.dto.Catalog.PondCreate;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.PondUpdate;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.Pond;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.repository.BatchRepository;
import com.lacocha.backend.repository.PondRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/** Ponds and batches created or edited from the web panel. */
@Service
public class CatalogService {

    private final EntityManager em;
    private final PondRepository ponds;
    private final BatchRepository batches;

    public CatalogService(EntityManager em, PondRepository ponds, BatchRepository batches) {
        this.em = em;
        this.ponds = ponds;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public List<PondResponse> listPonds() {
        return ponds.findAllByOrderByNameAsc().stream().map(PondResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PondResponse getPond(UUID id) {
        return PondResponse.from(ponds.findById(id).orElseThrow(() -> notFound("El estanque")));
    }

    @Transactional
    public PondResponse createPond(PondCreate data) {
        if (data.id() != null && ponds.existsById(data.id())) {
            throw alreadyExists("El estanque");
        }
        Pond pond = new Pond();
        pond.setId(data.id() != null ? data.id() : UUID.randomUUID());
        requireFreeName(pond.getId(), data.name());
        pond.setName(data.name());
        if (data.type() != null) {
            pond.setType(data.type());
        }
        pond.setVolumeM3(data.volumeM3());
        pond.setUpdatedAt(ServerClock.now());
        em.persist(pond);
        return PondResponse.from(pond);
    }

    @Transactional
    public PondResponse updatePond(UUID id, PondUpdate data) {
        Pond pond = ponds.findById(id).orElseThrow(() -> notFound("El estanque"));
        if (data.name() != null) {
            requireFreeName(id, data.name());
            pond.setName(data.name());
        }
        if (data.type() != null) pond.setType(data.type());
        if (data.volumeM3() != null) pond.setVolumeM3(data.volumeM3());
        if (data.active() != null) pond.setActive(data.active());
        pond.setUpdatedAt(ServerClock.now());
        ponds.flush();
        return PondResponse.from(pond);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listBatches(UUID pondId, String status) {
        if (status != null && !status.equals("activo") && !status.equals("cerrado")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado: debe ser activo o cerrado");
        }
        // Filter in the database instead of loading every batch into memory
        StringBuilder jpql = new StringBuilder("select b from Batch b where 1 = 1");
        if (pondId != null) jpql.append(" and b.pondId = :pond");
        if (status != null) jpql.append(" and b.status = :status");
        jpql.append(" order by b.code");

        TypedQuery<Batch> query = em.createQuery(jpql.toString(), Batch.class);
        if (pondId != null) query.setParameter("pond", pondId);
        if (status != null) query.setParameter("status", status);
        return query.getResultList().stream().map(BatchResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BatchResponse getBatch(UUID id) {
        return BatchResponse.from(batches.findById(id).orElseThrow(() -> notFound("El lote")));
    }

    @Transactional
    public BatchResponse createBatch(BatchCreate data) {
        if (!ponds.existsById(data.pondId())) {
            throw notFound("El estanque");
        }
        if (data.id() != null && batches.existsById(data.id())) {
            throw alreadyExists("El lote");
        }
        Batch batch = new Batch();
        batch.setId(data.id() != null ? data.id() : UUID.randomUUID());
        requireFreeCode(batch.getId(), data.pondId(), data.code());
        batch.setPondId(data.pondId());
        batch.setCode(data.code());
        batch.setStockingDate(data.stockingDate());
        batch.setInitialQuantity(data.initialQuantity());
        batch.setInitialWeightG(data.initialWeightG());
        if (data.status() != null) {
            batch.setStatus(data.status());
        }
        batch.setClosingDate(data.closingDate());
        adjustClosing(batch);
        requireValidClosing(batch);
        batch.setUpdatedAt(ServerClock.now());
        em.persist(batch);
        return BatchResponse.from(batch);
    }

    @Transactional
    public BatchResponse updateBatch(UUID id, BatchUpdate data) {
        Batch batch = batches.findById(id).orElseThrow(() -> notFound("El lote"));
        if (data.code() != null) {
            requireFreeCode(id, batch.getPondId(), data.code());
            batch.setCode(data.code());
        }
        if (data.stockingDate() != null) batch.setStockingDate(data.stockingDate());
        if (data.initialQuantity() != null) batch.setInitialQuantity(data.initialQuantity());
        if (data.initialWeightG() != null) batch.setInitialWeightG(data.initialWeightG());
        if (data.status() != null) batch.setStatus(data.status());
        if (data.closingDate() != null) batch.setClosingDate(data.closingDate());
        adjustClosing(batch);
        requireValidClosing(batch);
        batch.setUpdatedAt(ServerClock.now());
        batches.flush();
        return BatchResponse.from(batch);
    }

    static final String NAME_TAKEN = "ya existe otro estanque con ese nombre";
    static final String CODE_TAKEN = "ya existe otro lote con ese código en el estanque";

    private void requireFreeName(UUID pondId, String name) {
        if (ponds.existsByNameAndIdNot(name, pondId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "nombre: " + NAME_TAKEN);
        }
    }

    private void requireFreeCode(UUID batchId, UUID pondId, String code) {
        if (batches.existsByPondIdAndCodeAndIdNot(pondId, code, batchId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "codigo: " + CODE_TAKEN);
        }
    }

    /**
     * The database requires a closed batch to have a closing date and an active one not to have it.
     * Closing without a date means "closed today" (farm time, never before the stocking date);
     * reopening a batch means the closing date no longer applies.
     */
    static void adjustClosing(Batch batch) {
        if (!"cerrado".equals(batch.getStatus())) {
            batch.setClosingDate(null);
        } else if (batch.getClosingDate() == null) {
            LocalDate today = LocalDate.now(Dates.FARM_ZONE);
            LocalDate stocking = batch.getStockingDate();
            batch.setClosingDate(stocking != null && stocking.isAfter(today) ? stocking : today);
        }
    }

    /** null when the dates are fine. The database also checks it (ck_lotes_orden_fechas). */
    static String closingError(LocalDate stocking, LocalDate closing) {
        return closing != null && stocking != null && closing.isBefore(stocking)
                ? "fecha_cierre: no puede ser anterior a la fecha de siembra"
                : null;
    }

    private static void requireValidClosing(Batch batch) {
        String error = closingError(batch.getStockingDate(), batch.getClosingDate());
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, error);
        }
    }

    /** what is the Spanish subject shown to the user, e.g. "El lote". */
    static ResponseStatusException alreadyExists(String what) {
        return new ResponseStatusException(HttpStatus.CONFLICT, what + " ya existe con ese id");
    }

    static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " no existe");
    }
}
