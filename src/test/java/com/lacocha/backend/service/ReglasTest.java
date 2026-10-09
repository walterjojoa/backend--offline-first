package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReglasTest {

    @Test
    void lecturaEnRangoNoGeneraAlertas() {
        assertThat(Rules.evaluateReading(12.0, 7.2, 8.0)).isEmpty();
    }

    @Test
    void valoresNulosSeIgnoran() {
        assertThat(Rules.evaluateReading(null, null, null)).isEmpty();
    }

    @Test
    void temperaturaAltaEsAdvertenciaAntesDelLimiteCritico() {
        List<Rules.Result> r = Rules.evaluateReading(17.0, null, null);
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.variable()).isEqualTo("temp_c");
            assertThat(x.level()).isEqualTo("advertencia");
            assertThat(x.message()).isEqualTo("Temperatura alta: 17 °C (óptimo 10–16 °C)");
        });
    }

    @Test
    void oxigenoBajoEsCritico() {
        List<Rules.Result> r = Rules.evaluateReading(null, null, 4.0);
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.level()).isEqualTo("critica");
            assertThat(x.message()).isEqualTo("Oxígeno disuelto bajo: 4 mg/L (mínimo 6 mg/L)");
        });
    }

    @Test
    void variasVariablesFueraDeRango() {
        List<Rules.Result> r = Rules.evaluateReading(5.0, 9.5, null);
        assertThat(r).extracting(Rules.Result::variable).containsExactly("temp_c", "ph");
        assertThat(r).extracting(Rules.Result::level).containsOnly("critica");
    }

    @Test
    void tasaDeAlimentacionSegunPesoYTemperatura() {
        assertThat(Rules.feedingRatePct(2.5, 12.0)).isEqualTo(4.5);
        assertThat(Rules.feedingRatePct(0.5, 9.0)).isEqualTo(4.5); // 6 % x 0.75
        assertThat(Rules.feedingRatePct(100.0, 14.0)).isEqualTo(1.5);
    }

    @Test
    void sobre18GradosSeSuspendeLaAlimentacion() {
        assertThat(Rules.feedingRatePct(10.0, 19.0)).isZero();
        assertThat(Rules.dailyRationKg(50.0, 10.0, 19.0)).isZero();
    }

    @Test
    void racionDiaria() {
        assertThat(Rules.dailyRationKg(10.0, 30.0, 17.0)).isEqualTo(0.154); // 10 kg x 2.2 % x 0.7
    }

    @Test
    void round() {
        assertThat(Rules.round(12.34567, 2)).isEqualTo(12.35);
        assertThat(Rules.round(0.0005, 3)).isEqualTo(0.001);
    }
}
