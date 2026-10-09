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
import com.lacocha.backend.dto.Catalogo.EstanqueSalida;
import com.lacocha.backend.dto.Catalogo.EstanqueSync;
import com.lacocha.backend.dto.Catalogo.LoteSalida;
import com.lacocha.backend.dto.Catalogo.LoteSync;
import com.lacocha.backend.dto.Eventos;
import com.lacocha.backend.dto.Eventos.Entrada;
import com.lacocha.backend.dto.Eventos.LecturaAguaEntrada;
import com.lacocha.backend.dto.Sync.AlertaSalida;
import com.lacocha.backend.dto.Sync.PullRespuesta;
import com.lacocha.backend.dto.Sync.PushPeticion;
import com.lacocha.backend.dto.Sync.PushRespuesta;
import com.lacocha.backend.dto.Sync.Rechazo;
import com.lacocha.backend.model.Alerta;
import com.lacocha.backend.model.Estanque;
import com.lacocha.backend.model.Evento;
import com.lacocha.backend.model.Lote;
import com.lacocha.backend.model.Reloj;
import com.lacocha.backend.repository.AlertaRepository;
import com.lacocha.backend.repository.EstanqueRepository;
import com.lacocha.backend.repository.LoteRepository;

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
    private final EstanqueRepository estanques;
    private final LoteRepository lotes;
    private final AlertaRepository alertas;
    private final ObjectMapper mapper;
    private final Validator validator;

    public SyncService(EntityManager em, EstanqueRepository estanques, LoteRepository lotes,
            AlertaRepository alertas, ObjectMapper mapper, Validator validator) {
        this.em = em;
        this.estanques = estanques;
        this.lotes = lotes;
        this.alertas = alertas;
        this.mapper = mapper;
        this.validator = validator;
    }

    /** Resultado que se va llenando durante el push. */
    private static final class Resultado {
        final List<UUID> aceptados = new ArrayList<>();
        final List<UUID> duplicados = new ArrayList<>();
        final List<UUID> obsoletos = new ArrayList<>();
        final List<Rechazo> rechazados = new ArrayList<>();
        int alertasGeneradas = 0;
    }

    private static class EventoInvalido extends Exception {
        EventoInvalido(String mensaje) {
            super(mensaje);
        }
    }

    @Transactional
    public PushRespuesta push(PushPeticion peticion) {
        Instant servidorEn = Reloj.ahora();
        Resultado r = new Resultado();

        for (EstanqueSync e : lista(peticion.estanques())) {
            String error = Fechas.errorFechaDispositivo(e.actualizadoEn());
            if (error != null) {
                r.rechazados.add(new Rechazo(e.id().toString(), "actualizado_en: " + error));
                continue;
            }
            Estanque actual = estanques.findById(e.id()).orElse(null);
            boolean nuevo = actual == null;
            if (nuevo) {
                actual = new Estanque();
                actual.setId(e.id());
            } else if (!e.actualizadoEn().toInstant().isAfter(actual.getActualizadoEn())) {
                r.obsoletos.add(e.id());
                continue;
            }
            actual.setNombre(e.nombre());
            actual.setTipo(e.tipo() != null ? e.tipo() : "estanque");
            actual.setVolumenM3(e.volumenM3());
            actual.setActivo(e.activo() == null || e.activo());
            actual.setActualizadoEn(e.actualizadoEn().toInstant());
            if (nuevo) {
                em.persist(actual);
            }
            r.aceptados.add(e.id());
        }
        em.flush();

        for (LoteSync l : lista(peticion.lotes())) {
            String error = Fechas.errorFechaDispositivo(l.actualizadoEn());
            if (error != null) {
                r.rechazados.add(new Rechazo(l.id().toString(), "actualizado_en: " + error));
                continue;
            }
            if (!estanques.existsById(l.estanqueId())) {
                r.rechazados.add(new Rechazo(l.id().toString(), "estanque_id: el estanque no existe"));
                continue;
            }
            Lote actual = lotes.findById(l.id()).orElse(null);
            boolean nuevo = actual == null;
            if (nuevo) {
                actual = new Lote();
                actual.setId(l.id());
            } else if (!l.actualizadoEn().toInstant().isAfter(actual.getActualizadoEn())) {
                r.obsoletos.add(l.id());
                continue;
            }
            actual.setEstanqueId(l.estanqueId());
            actual.setCodigo(l.codigo());
            actual.setFechaSiembra(l.fechaSiembra());
            actual.setCantidadInicial(l.cantidadInicial());
            actual.setPesoInicialG(l.pesoInicialG());
            actual.setEstado(l.estado() != null ? l.estado() : "activo");
            actual.setActualizadoEn(l.actualizadoEn().toInstant());
            if (nuevo) {
                em.persist(actual);
            }
            r.aceptados.add(l.id());
        }
        em.flush();

        Set<UUID> padresConocidos = new HashSet<>();
        for (JsonNode crudo : lista(peticion.eventos())) {
            Entrada evento;
            try {
                evento = leerEvento(crudo);
            } catch (EventoInvalido ex) {
                r.rechazados.add(new Rechazo(idCrudo(crudo), ex.getMessage()));
                continue;
            }

            if (!padresConocidos.contains(evento.padreId())) {
                Class<?> clasePadre = evento.padreEsEstanque() ? Estanque.class : Lote.class;
                if (em.find(clasePadre, evento.padreId()) == null) {
                    String campo = evento.padreEsEstanque() ? "estanque_id" : "lote_id";
                    r.rechazados.add(new Rechazo(evento.id().toString(), campo + ": no existe"));
                    continue;
                }
                padresConocidos.add(evento.padreId());
            }

            Evento entidad = evento.aEntidad();
            if (em.find(entidad.getClass(), evento.id()) != null) {
                r.duplicados.add(evento.id());
                continue;
            }
            entidad.setId(evento.id());
            entidad.setDispositivoId(peticion.dispositivoId());
            entidad.setOrigen(evento.origen() != null ? evento.origen() : "manual");
            entidad.setRegistradoEn(evento.registradoEn().toInstant());
            entidad.setRecibidoEn(servidorEn);
            em.persist(entidad);
            r.aceptados.add(evento.id());

            if (evento instanceof LecturaAguaEntrada lectura) {
                for (Reglas.Resultado regla : Reglas.evaluarLectura(lectura.tempC(), lectura.ph(), lectura.oxigenoMgL())) {
                    // El sensor mide cada pocos segundos: si ya hay una alerta pendiente igual, no se repite
                    if (alertas.existsByEstanqueIdAndVariableAndNivelAndAtendidaFalse(
                            lectura.estanqueId(), regla.variable(), regla.nivel())) {
                        continue;
                    }
                    Alerta alerta = new Alerta();
                    alerta.setEstanqueId(lectura.estanqueId());
                    alerta.setLecturaId(lectura.id());
                    alerta.setVariable(regla.variable());
                    alerta.setValor(regla.valor());
                    alerta.setNivel(regla.nivel());
                    alerta.setMensaje(regla.mensaje());
                    alerta.setMedidoEn(lectura.registradoEn().toInstant());
                    em.persist(alerta);
                    r.alertasGeneradas++;
                }
            }
        }

        log.info("push de {}: {} aceptados, {} duplicados, {} obsoletos, {} rechazados, {} alertas",
                peticion.dispositivoId(), r.aceptados.size(), r.duplicados.size(), r.obsoletos.size(),
                r.rechazados.size(), r.alertasGeneradas);
        return new PushRespuesta(r.aceptados, r.duplicados, r.obsoletos, r.rechazados, r.alertasGeneradas, servidorEn);
    }

    /** desde es el servidor_en que devolvió el pull anterior. Sin él, baja todo. */
    @Transactional(readOnly = true)
    public PullRespuesta pull(Instant desde) {
        Instant servidorEn = Reloj.ahora();
        List<Estanque> listaEstanques = desde == null ? estanques.findAll() : estanques.findByServidorEnAfter(desde);
        List<Lote> listaLotes = desde == null ? lotes.findAll() : lotes.findByServidorEnAfter(desde);
        return new PullRespuesta(
                listaEstanques.stream().map(EstanqueSalida::de).toList(),
                listaLotes.stream().map(LoteSalida::de).toList(),
                alertas.findTop100ByAtendidaFalseOrderByMedidoEnDesc().stream().map(AlertaSalida::de).toList(),
                servidorEn);
    }

    private Entrada leerEvento(JsonNode crudo) throws EventoInvalido {
        if (crudo == null || !crudo.isObject()) {
            throw new EventoInvalido("el evento debe ser un objeto JSON");
        }
        Class<? extends Entrada> clase = Eventos.TIPOS.get(crudo.path("tipo").asText(""));
        if (clase == null) {
            throw new EventoInvalido("tipo: debe ser uno de " + String.join(", ", Eventos.TIPOS.keySet()));
        }

        Entrada evento;
        try {
            evento = mapper.treeToValue(crudo, clase);
        } catch (JsonProcessingException ex) {
            throw new EventoInvalido(campoConError(ex) + ": formato no válido");
        }

        Set<ConstraintViolation<Entrada>> violaciones = validator.validate(evento);
        if (!violaciones.isEmpty()) {
            ConstraintViolation<Entrada> v = violaciones.stream()
                    .min(Comparator.comparing(x -> x.getPropertyPath().toString()))
                    .get();
            throw new EventoInvalido(aSnake(v.getPropertyPath().toString()) + ": " + v.getMessage());
        }

        String error = Fechas.errorFechaDispositivo(evento.registradoEn());
        if (error != null) {
            throw new EventoInvalido("registrado_en: " + error);
        }
        error = evento.errorExtra();
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

    private static String aSnake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String idCrudo(JsonNode crudo) {
        return crudo != null && crudo.hasNonNull("id") ? crudo.get("id").asText() : null;
    }

    private static <T> List<T> lista(List<T> l) {
        return l != null ? l : List.of();
    }
}
