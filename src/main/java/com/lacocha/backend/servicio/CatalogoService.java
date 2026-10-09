package com.lacocha.backend.servicio;

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
import com.lacocha.backend.modelo.Estanque;
import com.lacocha.backend.modelo.Lote;
import com.lacocha.backend.modelo.Reloj;
import com.lacocha.backend.repositorio.EstanqueRepository;
import com.lacocha.backend.repositorio.LoteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
public class CatalogoService {

    private final EntityManager em;
    private final EstanqueRepository estanques;
    private final LoteRepository lotes;

    public CatalogoService(EntityManager em, EstanqueRepository estanques, LoteRepository lotes) {
        this.em = em;
        this.estanques = estanques;
        this.lotes = lotes;
    }

    @Transactional(readOnly = true)
    public List<EstanqueSalida> listarEstanques() {
        return estanques.findAllByOrderByNombreAsc().stream().map(EstanqueSalida::de).toList();
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
        Estanque e = new Estanque();
        e.setId(datos.id() != null ? datos.id() : UUID.randomUUID());
        e.setNombre(datos.nombre());
        if (datos.tipo() != null) {
            e.setTipo(datos.tipo());
        }
        e.setVolumenM3(datos.volumenM3());
        e.setActualizadoEn(Reloj.ahora());
        em.persist(e);
        return EstanqueSalida.de(e);
    }

    @Transactional
    public EstanqueSalida editarEstanque(UUID id, EstanqueEditar datos) {
        Estanque e = estanques.findById(id).orElseThrow(() -> noExiste("El estanque"));
        if (datos.nombre() != null) e.setNombre(datos.nombre());
        if (datos.tipo() != null) e.setTipo(datos.tipo());
        if (datos.volumenM3() != null) e.setVolumenM3(datos.volumenM3());
        if (datos.activo() != null) e.setActivo(datos.activo());
        e.setActualizadoEn(Reloj.ahora());
        estanques.flush();
        return EstanqueSalida.de(e);
    }

    @Transactional(readOnly = true)
    public List<LoteSalida> listarLotes(UUID estanqueId, String estado) {
        if (estado != null && !estado.equals("activo") && !estado.equals("cerrado")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado: debe ser activo o cerrado");
        }
        // Filtra en la base de datos en vez de traer todos los lotes a memoria
        StringBuilder jpql = new StringBuilder("select l from Lote l where 1 = 1");
        if (estanqueId != null) jpql.append(" and l.estanqueId = :estanque");
        if (estado != null) jpql.append(" and l.estado = :estado");
        jpql.append(" order by l.codigo");

        TypedQuery<Lote> consulta = em.createQuery(jpql.toString(), Lote.class);
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
        Lote l = new Lote();
        l.setId(datos.id() != null ? datos.id() : UUID.randomUUID());
        l.setEstanqueId(datos.estanqueId());
        l.setCodigo(datos.codigo());
        l.setFechaSiembra(datos.fechaSiembra());
        l.setCantidadInicial(datos.cantidadInicial());
        l.setPesoInicialG(datos.pesoInicialG());
        if (datos.estado() != null) {
            l.setEstado(datos.estado());
        }
        l.setActualizadoEn(Reloj.ahora());
        em.persist(l);
        return LoteSalida.de(l);
    }

    @Transactional
    public LoteSalida editarLote(UUID id, LoteEditar datos) {
        Lote l = lotes.findById(id).orElseThrow(() -> noExiste("El lote"));
        if (datos.codigo() != null) l.setCodigo(datos.codigo());
        if (datos.fechaSiembra() != null) l.setFechaSiembra(datos.fechaSiembra());
        if (datos.cantidadInicial() != null) l.setCantidadInicial(datos.cantidadInicial());
        if (datos.pesoInicialG() != null) l.setPesoInicialG(datos.pesoInicialG());
        if (datos.estado() != null) l.setEstado(datos.estado());
        l.setActualizadoEn(Reloj.ahora());
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
