package com.lacocha.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.dto.Catalog.PondCreate;
import com.lacocha.backend.dto.Catalog.PondUpdate;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.BatchCreate;
import com.lacocha.backend.dto.Catalog.BatchUpdate;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.model.Pond;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.ServerClock;
import com.lacocha.backend.repository.PondRepository;
import com.lacocha.backend.repository.BatchRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
public class CatalogoService {

    private final EntityManager em;
    private final PondRepository ponds;
    private final BatchRepository batches;

    public CatalogoService(EntityManager em, PondRepository ponds, BatchRepository batches) {
        this.em = em;
        this.ponds = ponds;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public List<PondResponse> listarEstanques() {
        return ponds.findAllByOrderByNameAsc().stream().map(PondResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PondResponse obtenerEstanque(UUID id) {
        return PondResponse.from(ponds.findById(id).orElseThrow(() -> noExiste("El estanque")));
    }

    @Transactional
    public PondResponse crearEstanque(PondCreate datos) {
        if (datos.id() != null && ponds.existsById(datos.id())) {
            throw yaExiste("El estanque");
        }
        Pond e = new Pond();
        e.setId(datos.id() != null ? datos.id() : UUID.randomUUID());
        e.setName(datos.name());
        if (datos.type() != null) {
            e.setType(datos.type());
        }
        e.setVolumeM3(datos.volumeM3());
        e.setUpdatedAt(ServerClock.now());
        em.persist(e);
        return PondResponse.from(e);
    }

    @Transactional
    public PondResponse editarEstanque(UUID id, PondUpdate datos) {
        Pond e = ponds.findById(id).orElseThrow(() -> noExiste("El estanque"));
        if (datos.name() != null) e.setName(datos.name());
        if (datos.type() != null) e.setType(datos.type());
        if (datos.volumeM3() != null) e.setVolumeM3(datos.volumeM3());
        if (datos.active() != null) e.setActive(datos.active());
        e.setUpdatedAt(ServerClock.now());
        ponds.flush();
        return PondResponse.from(e);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listarLotes(UUID pondId, String status) {
        if (status != null && !status.equals("activo") && !status.equals("cerrado")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado: debe ser activo o cerrado");
        }
        // Filtra en la base de datos en vez de traer todos los lotes a memoria
        StringBuilder jpql = new StringBuilder("select l from Batch l where 1 = 1");
        if (pondId != null) jpql.append(" and l.pondId = :estanque");
        if (status != null) jpql.append(" and l.status = :estado");
        jpql.append(" order by l.code");

        TypedQuery<Batch> consulta = em.createQuery(jpql.toString(), Batch.class);
        if (pondId != null) consulta.setParameter("estanque", pondId);
        if (status != null) consulta.setParameter("estado", status);
        return consulta.getResultList().stream().map(BatchResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BatchResponse obtenerLote(UUID id) {
        return BatchResponse.from(batches.findById(id).orElseThrow(() -> noExiste("El lote")));
    }

    @Transactional
    public BatchResponse crearLote(BatchCreate datos) {
        if (!ponds.existsById(datos.pondId())) {
            throw noExiste("El estanque");
        }
        if (datos.id() != null && batches.existsById(datos.id())) {
            throw yaExiste("El lote");
        }
        Batch l = new Batch();
        l.setId(datos.id() != null ? datos.id() : UUID.randomUUID());
        l.setPondId(datos.pondId());
        l.setCode(datos.code());
        l.setStockingDate(datos.stockingDate());
        l.setInitialQuantity(datos.initialQuantity());
        l.setInitialWeightG(datos.initialWeightG());
        if (datos.status() != null) {
            l.setStatus(datos.status());
        }
        l.setUpdatedAt(ServerClock.now());
        em.persist(l);
        return BatchResponse.from(l);
    }

    @Transactional
    public BatchResponse editarLote(UUID id, BatchUpdate datos) {
        Batch l = batches.findById(id).orElseThrow(() -> noExiste("El lote"));
        if (datos.code() != null) l.setCode(datos.code());
        if (datos.stockingDate() != null) l.setStockingDate(datos.stockingDate());
        if (datos.initialQuantity() != null) l.setInitialQuantity(datos.initialQuantity());
        if (datos.initialWeightG() != null) l.setInitialWeightG(datos.initialWeightG());
        if (datos.status() != null) l.setStatus(datos.status());
        l.setUpdatedAt(ServerClock.now());
        batches.flush();
        return BatchResponse.from(l);
    }

    static ResponseStatusException yaExiste(String que) {
        return new ResponseStatusException(HttpStatus.CONFLICT, que + " ya existe con ese id");
    }

    static ResponseStatusException noExiste(String que) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, que + " no existe");
    }
}
