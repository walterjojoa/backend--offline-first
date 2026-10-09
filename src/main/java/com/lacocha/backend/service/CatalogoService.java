package com.lacocha.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.lacocha.backend.dto.Catalogo.EstanqueCrear;
import com.lacocha.backend.dto.Catalogo.EstanqueEditar;
import com.lacocha.backend.dto.Catalogo.EstanqueSalida;
import com.lacocha.backend.dto.Catalogo.LoteCrear;
import com.lacocha.backend.dto.Catalogo.LoteEditar;
import com.lacocha.backend.dto.Catalogo.LoteSalida;
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
    private final PondRepository estanques;
    private final BatchRepository lotes;

    public CatalogoService(EntityManager em, PondRepository estanques, BatchRepository lotes) {
        this.em = em;
        this.estanques = estanques;
        this.lotes = lotes;
    }

    @Transactional(readOnly = true)
    public List<EstanqueSalida> listarEstanques() {
        return estanques.findAllByOrderByNameAsc().stream().map(EstanqueSalida::de).toList();
    }

    @Transactional(readOnly = true)
    public EstanqueSalida obtenerEstanque(UUID id) {
        return EstanqueSalida.de(estanques.findById(id).orElseThrow(() -> noExiste("El estanque")));
    }

    @Transactional
    public EstanqueSalida crearEstanque(EstanqueCrear datos) {
        if (datos.id() != null && estanques.existsById(datos.id())) {
            throw yaExiste("El estanque");
        }
        Pond e = new Pond();
        e.setId(datos.id() != null ? datos.id() : UUID.randomUUID());
        e.setName(datos.nombre());
        if (datos.tipo() != null) {
            e.setType(datos.tipo());
        }
        e.setVolumeM3(datos.volumenM3());
        e.setUpdatedAt(ServerClock.now());
        em.persist(e);
        return EstanqueSalida.de(e);
    }

    @Transactional
    public EstanqueSalida editarEstanque(UUID id, EstanqueEditar datos) {
        Pond e = estanques.findById(id).orElseThrow(() -> noExiste("El estanque"));
        if (datos.nombre() != null) e.setName(datos.nombre());
        if (datos.tipo() != null) e.setType(datos.tipo());
        if (datos.volumenM3() != null) e.setVolumeM3(datos.volumenM3());
        if (datos.activo() != null) e.setActive(datos.activo());
        e.setUpdatedAt(ServerClock.now());
        estanques.flush();
        return EstanqueSalida.de(e);
    }

    @Transactional(readOnly = true)
    public List<LoteSalida> listarLotes(UUID estanqueId, String estado) {
        if (estado != null && !estado.equals("activo") && !estado.equals("cerrado")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado: debe ser activo o cerrado");
        }
        // Filtra en la base de datos en vez de traer todos los lotes a memoria
        StringBuilder jpql = new StringBuilder("select l from Batch l where 1 = 1");
        if (estanqueId != null) jpql.append(" and l.pondId = :estanque");
        if (estado != null) jpql.append(" and l.status = :estado");
        jpql.append(" order by l.code");

        TypedQuery<Batch> consulta = em.createQuery(jpql.toString(), Batch.class);
        if (estanqueId != null) consulta.setParameter("estanque", estanqueId);
        if (estado != null) consulta.setParameter("estado", estado);
        return consulta.getResultList().stream().map(LoteSalida::de).toList();
    }

    @Transactional(readOnly = true)
    public LoteSalida obtenerLote(UUID id) {
        return LoteSalida.de(lotes.findById(id).orElseThrow(() -> noExiste("El lote")));
    }

    @Transactional
    public LoteSalida crearLote(LoteCrear datos) {
        if (!estanques.existsById(datos.estanqueId())) {
            throw noExiste("El estanque");
        }
        if (datos.id() != null && lotes.existsById(datos.id())) {
            throw yaExiste("El lote");
        }
        Batch l = new Batch();
        l.setId(datos.id() != null ? datos.id() : UUID.randomUUID());
        l.setPondId(datos.estanqueId());
        l.setCode(datos.codigo());
        l.setStockingDate(datos.fechaSiembra());
        l.setInitialQuantity(datos.cantidadInicial());
        l.setInitialWeightG(datos.pesoInicialG());
        if (datos.estado() != null) {
            l.setStatus(datos.estado());
        }
        l.setUpdatedAt(ServerClock.now());
        em.persist(l);
        return LoteSalida.de(l);
    }

    @Transactional
    public LoteSalida editarLote(UUID id, LoteEditar datos) {
        Batch l = lotes.findById(id).orElseThrow(() -> noExiste("El lote"));
        if (datos.codigo() != null) l.setCode(datos.codigo());
        if (datos.fechaSiembra() != null) l.setStockingDate(datos.fechaSiembra());
        if (datos.cantidadInicial() != null) l.setInitialQuantity(datos.cantidadInicial());
        if (datos.pesoInicialG() != null) l.setInitialWeightG(datos.pesoInicialG());
        if (datos.estado() != null) l.setStatus(datos.estado());
        l.setUpdatedAt(ServerClock.now());
        lotes.flush();
        return LoteSalida.de(l);
    }

    static ResponseStatusException yaExiste(String que) {
        return new ResponseStatusException(HttpStatus.CONFLICT, que + " ya existe con ese id");
    }

    static ResponseStatusException noExiste(String que) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, que + " no existe");
    }
}
