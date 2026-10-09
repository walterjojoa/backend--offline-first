package com.lacocha.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.lacocha.backend.model.Mortality;
import com.lacocha.backend.model.Feeding;
import com.lacocha.backend.model.Biometry;
import com.lacocha.backend.model.FryCount;
import com.lacocha.backend.model.WaterReading;

public final class Consultas {

    private Consultas() {
    }

    public record LecturaAguaSalida(UUID id, UUID estanqueId, Double tempC, Double ph, Double oxigenoMgL,
            String origen, Instant registradoEn) {

        public static LecturaAguaSalida de(WaterReading l) {
            return new LecturaAguaSalida(l.getId(), l.getPondId(), l.getTempC(), l.getPh(), l.getOxygenMgL(),
                    l.getSource(), l.getRecordedAt());
        }
    }

    /** Resumen de un día de lecturas de agua, para las gráficas del panel. */
    public record LecturasDia(LocalDate fecha, int lecturas,
            Double tempMin, Double tempMax, Double tempPromedio,
            Double phMin, Double phMax, Double phPromedio) {
    }

    public record ConteoSalida(UUID id, UUID loteId, Integer total, Integer cortesMultiples, String origen,
            String dispositivoId, Instant registradoEn) {

        public static ConteoSalida de(FryCount c) {
            return new ConteoSalida(c.getId(), c.getBatchId(), c.getTotal(), c.getMultipleCuts(), c.getSource(),
                    c.getDeviceId(), c.getRecordedAt());
        }
    }

    public record MortalidadSalida(UUID id, UUID loteId, Integer cantidad, String causa, String origen,
            String dispositivoId, Instant registradoEn) {

        public static MortalidadSalida de(Mortality m) {
            return new MortalidadSalida(m.getId(), m.getBatchId(), m.getQuantity(), m.getCause(), m.getSource(),
                    m.getDeviceId(), m.getRecordedAt());
        }
    }

    public record AlimentacionSalida(UUID id, UUID loteId, Double kg, String origen,
            String dispositivoId, Instant registradoEn) {

        public static AlimentacionSalida de(Feeding a) {
            return new AlimentacionSalida(a.getId(), a.getBatchId(), a.getKg(), a.getSource(),
                    a.getDeviceId(), a.getRecordedAt());
        }
    }

    public record BiometriaSalida(UUID id, UUID loteId, Double pesoPromedioG, Integer muestra, String origen,
            String dispositivoId, Instant registradoEn) {

        public static BiometriaSalida de(Biometry b) {
            return new BiometriaSalida(b.getId(), b.getBatchId(), b.getAvgWeightG(), b.getSampleSize(), b.getSource(),
                    b.getDeviceId(), b.getRecordedAt());
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
