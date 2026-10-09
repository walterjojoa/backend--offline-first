package com.lacocha.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.lacocha.backend.modelo.Mortalidad;
import com.lacocha.backend.modelo.Alimentacion;
import com.lacocha.backend.modelo.Biometria;
import com.lacocha.backend.modelo.Conteo;
import com.lacocha.backend.modelo.LecturaAgua;

public final class Consultas {

    private Consultas() {
    }

    public record LecturaAguaSalida(UUID id, UUID estanqueId, Double tempC, Double ph, Double oxigenoMgL,
            String origen, Instant registradoEn) {

        public static LecturaAguaSalida de(LecturaAgua l) {
            return new LecturaAguaSalida(l.getId(), l.getEstanqueId(), l.getTempC(), l.getPh(), l.getOxigenoMgL(),
                    l.getOrigen(), l.getRegistradoEn());
        }
    }

    /** Resumen de un día de lecturas de agua, para las gráficas del panel. */
    public record LecturasDia(LocalDate fecha, int lecturas,
            Double tempMin, Double tempMax, Double tempPromedio,
            Double phMin, Double phMax, Double phPromedio) {
    }

    public record ConteoSalida(UUID id, UUID loteId, Integer total, Integer cortesMultiples, String origen,
            String dispositivoId, Instant registradoEn) {

        public static ConteoSalida de(Conteo c) {
            return new ConteoSalida(c.getId(), c.getLoteId(), c.getTotal(), c.getCortesMultiples(), c.getOrigen(),
                    c.getDispositivoId(), c.getRegistradoEn());
        }
    }

    public record MortalidadSalida(UUID id, UUID loteId, Integer cantidad, String causa, String origen,
            String dispositivoId, Instant registradoEn) {

        public static MortalidadSalida de(Mortalidad m) {
            return new MortalidadSalida(m.getId(), m.getLoteId(), m.getCantidad(), m.getCausa(), m.getOrigen(),
                    m.getDispositivoId(), m.getRegistradoEn());
        }
    }

    public record AlimentacionSalida(UUID id, UUID loteId, Double kg, String origen,
            String dispositivoId, Instant registradoEn) {

        public static AlimentacionSalida de(Alimentacion a) {
            return new AlimentacionSalida(a.getId(), a.getLoteId(), a.getKg(), a.getOrigen(),
                    a.getDispositivoId(), a.getRegistradoEn());
        }
    }

    public record BiometriaSalida(UUID id, UUID loteId, Double pesoPromedioG, Integer muestra, String origen,
            String dispositivoId, Instant registradoEn) {

        public static BiometriaSalida de(Biometria b) {
            return new BiometriaSalida(b.getId(), b.getLoteId(), b.getPesoPromedioG(), b.getMuestra(), b.getOrigen(),
                    b.getDispositivoId(), b.getRegistradoEn());
        }
    }

    public record ResumenLote(
            UUID loteId,
            String codigo,
            UUID estanqueId,
            Long diasCultivo,
            Integer cantidadInicial,
            Integer poblacionEstimada,
            long mortalidadTotal,
            Double supervivenciaPct,
            Double pesoPromedioG,
            Double biomasaKg,
            Double densidadKgM3,
            Double ultimaTemperaturaC,
            Instant ultimaLecturaEn,
            Double tasaAlimentacionPct,
            Double racionDiariaKg,
            double alimentoUltimaSemanaKg,
            double alimentoTotalKg,
            Double conversionAlimenticia,
            long alertasPendientes,
            List<String> notas) {
    }
}
