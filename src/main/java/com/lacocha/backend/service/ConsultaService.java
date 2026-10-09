package com.lacocha.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lacocha.backend.dto.Consultas.ConteoSalida;
import com.lacocha.backend.dto.Consultas.BiometriaSalida;
import com.lacocha.backend.dto.Consultas.AlimentacionSalida;
import com.lacocha.backend.dto.Consultas.MortalidadSalida;
import com.lacocha.backend.dto.Consultas.LecturaAguaSalida;
import com.lacocha.backend.dto.Consultas.LecturasDia;
import com.lacocha.backend.dto.Consultas.ResumenLote;
import com.lacocha.backend.dto.Sync.AlertaSalida;
import com.lacocha.backend.model.Alerta;
import com.lacocha.backend.model.Biometria;
import com.lacocha.backend.model.Conteo;
import com.lacocha.backend.model.Alimentacion;
import com.lacocha.backend.model.Mortalidad;
import com.lacocha.backend.model.Evento;
import com.lacocha.backend.model.LecturaAgua;
import com.lacocha.backend.model.Lote;
import com.lacocha.backend.model.Reloj;
import com.lacocha.backend.repository.AlertaRepository;
import com.lacocha.backend.repository.AlimentacionRepository;
import com.lacocha.backend.repository.BiometriaRepository;
import com.lacocha.backend.repository.ConteoRepository;
import com.lacocha.backend.repository.EstanqueRepository;
import com.lacocha.backend.repository.LecturaAguaRepository;
import com.lacocha.backend.repository.LoteRepository;
import com.lacocha.backend.repository.MortalidadRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
@Transactional(readOnly = true)
public class ConsultaService {

    private final EntityManager em;
    private final EstanqueRepository estanques;
    private final LoteRepository lotes;
    private final ConteoRepository conteos;
    private final MortalidadRepository mortalidades;
    private final BiometriaRepository biometrias;
    private final LecturaAguaRepository lecturas;
    private final AlimentacionRepository alimentaciones;
    private final AlertaRepository alertas;

    public ConsultaService(EntityManager em, EstanqueRepository estanques, LoteRepository lotes,
            ConteoRepository conteos, MortalidadRepository mortalidades, BiometriaRepository biometrias,
            LecturaAguaRepository lecturas, AlimentacionRepository alimentaciones, AlertaRepository alertas) {
        this.em = em;
        this.estanques = estanques;
        this.lotes = lotes;
        this.conteos = conteos;
        this.mortalidades = mortalidades;
        this.biometrias = biometrias;
        this.lecturas = lecturas;
        this.alimentaciones = alimentaciones;
        this.alertas = alertas;
    }

    public List<LecturaAguaSalida> lecturasEstanque(UUID estanqueId, Instant desde, Instant hasta, int limite) {
        if (!estanques.existsById(estanqueId)) {
            throw CatalogoService.noExiste("El estanque");
        }
        StringBuilder jpql = new StringBuilder("select l from LecturaAgua l where l.estanqueId = :estanque");
        if (desde != null) jpql.append(" and l.registradoEn >= :desde");
        if (hasta != null) jpql.append(" and l.registradoEn <= :hasta");
        jpql.append(" order by l.registradoEn desc");

        TypedQuery<LecturaAgua> consulta = em.createQuery(jpql.toString(), LecturaAgua.class)
                .setParameter("estanque", estanqueId)
                .setMaxResults(Math.max(1, Math.min(limite, 5000)));
        if (desde != null) consulta.setParameter("desde", desde);
        if (hasta != null) consulta.setParameter("hasta", hasta);
        return consulta.getResultList().stream().map(LecturaAguaSalida::de).toList();
    }

    /** Mínimo, máximo y promedio de temperatura y pH por día (hora de Colombia) de los últimos días. */
    public List<LecturasDia> lecturasPorDia(UUID estanqueId, int dias) {
        if (!estanques.existsById(estanqueId)) {
            throw CatalogoService.noExiste("El estanque");
        }
        dias = Math.max(1, Math.min(dias, 90));
        Instant desde = LocalDate.now(Fechas.ZONA_GRANJA).minusDays(dias - 1L)
                .atStartOfDay(Fechas.ZONA_GRANJA).toInstant();
        List<LecturaAgua> lista = em.createQuery(
                        "select l from LecturaAgua l where l.estanqueId = :estanque and l.registradoEn >= :desde",
                        LecturaAgua.class)
                .setParameter("estanque", estanqueId)
                .setParameter("desde", desde)
                .getResultList();

        Map<LocalDate, List<LecturaAgua>> porDia = lista.stream().collect(Collectors.groupingBy(
                l -> LocalDate.ofInstant(l.getRegistradoEn(), Fechas.ZONA_GRANJA), TreeMap::new, Collectors.toList()));

        return porDia.entrySet().stream().map(dia -> {
            DoubleSummaryStatistics temp = estadisticas(dia.getValue(), LecturaAgua::getTempC);
            DoubleSummaryStatistics ph = estadisticas(dia.getValue(), LecturaAgua::getPh);
            return new LecturasDia(dia.getKey(), dia.getValue().size(),
                    minimo(temp), maximo(temp), promedio(temp, 2),
                    minimo(ph), maximo(ph), promedio(ph, 2));
        }).toList();
    }

    private static DoubleSummaryStatistics estadisticas(List<LecturaAgua> lista, Function<LecturaAgua, Double> campo) {
        return lista.stream().map(campo).filter(Objects::nonNull).mapToDouble(Double::doubleValue).summaryStatistics();
    }

    private static Double minimo(DoubleSummaryStatistics e) {
        return e.getCount() > 0 ? e.getMin() : null;
    }

    private static Double maximo(DoubleSummaryStatistics e) {
        return e.getCount() > 0 ? e.getMax() : null;
    }

    private static Double promedio(DoubleSummaryStatistics e, int decimales) {
        return e.getCount() > 0 ? Reglas.redondear(e.getAverage(), decimales) : null;
    }

    public List<ConteoSalida> conteosLote(UUID loteId, int limite) {
        return eventosLote(Conteo.class, loteId, limite).stream().map(ConteoSalida::de).toList();
    }

    public List<MortalidadSalida> mortalidadesLote(UUID loteId, int limite) {
        return eventosLote(Mortalidad.class, loteId, limite).stream().map(MortalidadSalida::de).toList();
    }

    public List<AlimentacionSalida> alimentacionesLote(UUID loteId, int limite) {
        return eventosLote(Alimentacion.class, loteId, limite).stream().map(AlimentacionSalida::de).toList();
    }

    public List<BiometriaSalida> biometriasLote(UUID loteId, int limite) {
        return eventosLote(Biometria.class, loteId, limite).stream().map(BiometriaSalida::de).toList();
    }

    /** Historial de un tipo de evento del lote, del más reciente al más antiguo. */
    private <T extends Evento> List<T> eventosLote(Class<T> clase, UUID loteId, int limite) {
        if (!lotes.existsById(loteId)) {
            throw CatalogoService.noExiste("El lote");
        }
        return em.createQuery("select e from " + clase.getSimpleName()
                        + " e where e.loteId = :lote order by e.registradoEn desc", clase)
                .setParameter("lote", loteId)
                .setMaxResults(Math.max(1, Math.min(limite, 1000)))
                .getResultList();
    }

    public ResumenLote resumenLote(UUID loteId) {
        Lote lote = lotes.findById(loteId).orElseThrow(() -> CatalogoService.noExiste("El lote"));
        List<String> notas = new ArrayList<>();

        List<Conteo> listaConteos = conteos.findByLoteIdOrderByRegistradoEnAsc(loteId);
        Integer cantidadInicial = lote.getCantidadInicial() != null ? lote.getCantidadInicial()
                : (listaConteos.isEmpty() ? null : listaConteos.get(0).getTotal());

        // Población = último conteo menos las muertes registradas después de ese conteo
        long mortalidadTotal = mortalidades.totalDelLote(loteId);
        Integer poblacion;
        if (!listaConteos.isEmpty()) {
            Conteo ultimo = listaConteos.get(listaConteos.size() - 1);
            long despues = mortalidades.totalDelLoteDespuesDe(loteId, ultimo.getRegistradoEn());
            poblacion = (int) Math.max(ultimo.getTotal() - despues, 0);
        } else if (cantidadInicial != null) {
            poblacion = (int) Math.max(cantidadInicial - mortalidadTotal, 0);
        } else {
            poblacion = null;
            notas.add("Sin conteo ni cantidad inicial: no se puede estimar la población.");
        }

        Double supervivencia = poblacion != null && cantidadInicial != null && cantidadInicial > 0
                ? Reglas.redondear(poblacion * 100.0 / cantidadInicial, 1)
                : null;

        Double peso = biometrias.findFirstByLoteIdOrderByRegistradoEnDesc(loteId)
                .map(Biometria::getPesoPromedioG)
                .orElse(lote.getPesoInicialG());
        if (peso == null) {
            notas.add("Falta el peso promedio: registra una biometría para calcular biomasa y ración.");
        }
        Double biomasa = poblacion != null && peso != null ? Reglas.redondear(poblacion * peso / 1000, 3) : null;

        // Densidad de siembra: sirve para saber si el estanque está sobrecargado
        Double volumen = estanques.findById(lote.getEstanqueId()).map(e -> e.getVolumenM3()).orElse(null);
        Double densidad = biomasa != null && volumen != null && volumen > 0
                ? Reglas.redondear(biomasa / volumen, 2)
                : null;

        LecturaAgua lectura = lecturas.findFirstByEstanqueIdAndTempCIsNotNullOrderByRegistradoEnDesc(lote.getEstanqueId())
                .orElse(null);
        Double temp = lectura != null ? lectura.getTempC() : null;
        if (lectura == null) {
            notas.add("No hay lecturas de temperatura del estanque.");
        }

        Double tasa = null;
        Double racion = null;
        if (peso != null && temp != null) {
            tasa = Reglas.tasaAlimentacionPct(peso, temp);
            if (biomasa != null) {
                racion = Reglas.racionDiariaKg(biomasa, peso, temp);
            }
            if (tasa == 0) {
                notas.add("Agua sobre 18 °C: se recomienda suspender la alimentación.");
            }
        }

        Long diasCultivo = lote.getFechaSiembra() != null
                ? ChronoUnit.DAYS.between(lote.getFechaSiembra(), LocalDate.now(Fechas.ZONA_GRANJA))
                : null;

        double alimentoSemana = alimentaciones.kgDelLoteDesde(loteId, Instant.now().minus(Duration.ofDays(7)));

        // Factor de conversión alimenticia (FCA): kg de alimento por cada kg de biomasa ganada
        double alimentoTotal = alimentaciones.kgTotalDelLote(loteId);
        Double conversion = null;
        if (biomasa != null && cantidadInicial != null && lote.getPesoInicialG() != null && alimentoTotal > 0) {
            double ganancia = biomasa - cantidadInicial * lote.getPesoInicialG() / 1000;
            if (ganancia > 0) {
                conversion = Reglas.redondear(alimentoTotal / ganancia, 2);
            }
        }

        return new ResumenLote(
                lote.getId(), lote.getCodigo(), lote.getEstanqueId(), diasCultivo,
                cantidadInicial, poblacion, mortalidadTotal, supervivencia,
                peso, biomasa, densidad, temp, lectura != null ? lectura.getRegistradoEn() : null,
                tasa, racion, Reglas.redondear(alimentoSemana, 3),
                Reglas.redondear(alimentoTotal, 3), conversion,
                alertas.countByEstanqueIdAndAtendidaFalse(lote.getEstanqueId()),
                notas);
    }

    public List<AlertaSalida> listarAlertas(boolean pendientes, UUID estanqueId, int limite) {
        StringBuilder jpql = new StringBuilder("select a from Alerta a where 1 = 1");
        if (pendientes) jpql.append(" and a.atendida = false");
        if (estanqueId != null) jpql.append(" and a.estanqueId = :estanque");
        jpql.append(" order by a.medidoEn desc");

        TypedQuery<Alerta> consulta = em.createQuery(jpql.toString(), Alerta.class)
                .setMaxResults(Math.max(1, Math.min(limite, 1000)));
        if (estanqueId != null) consulta.setParameter("estanque", estanqueId);
        return consulta.getResultList().stream().map(AlertaSalida::de).toList();
    }

    @Transactional
    public AlertaSalida atenderAlerta(UUID id) {
        Alerta alerta = alertas.findById(id).orElseThrow(() -> CatalogoService.noExiste("La alerta"));
        // Atender dos veces no cambia la hora original
        if (!alerta.isAtendida()) {
            alerta.setAtendida(true);
            alerta.setAtendidaEn(Reloj.ahora());
        }
        return AlertaSalida.de(alerta);
    }
}
