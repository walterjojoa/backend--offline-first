package com.lacocha.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class RulesTest {

    private static final double INF = Double.POSITIVE_INFINITY;

    /** The same reference values that migrations V17 and V19 load for rainbow trout. */
    static final Rules TROUT = new Rules(
            List.of(new Rules.Range("temp_c", "Temperatura", true, " °C", 10.0, 16.0, 6.0, 18.0),
                    new Rules.Range("ph", "pH", false, "", 6.5, 8.5, 6.0, 9.0),
                    new Rules.Range("oxigeno_mg_l", "Oxígeno disuelto", false, " mg/L", 6.0, INF, 5.0, INF)),
            List.of(new Rules.Step(1.0, 6.0), new Rules.Step(5.0, 4.5), new Rules.Step(20.0, 3.0),
                    new Rules.Step(50.0, 2.2), new Rules.Step(INF, 1.5)),
            List.of(new Rules.Step(8.0, 0.5), new Rules.Step(10.0, 0.75), new Rules.Step(16.0, 1.0),
                    new Rules.Step(18.0, 0.7), new Rules.Step(INF, 0.0)));

    @Test
    void readingInRangeCreatesNoAlerts() {
        assertThat(TROUT.evaluateReading(12.0, 7.2, 8.0)).isEmpty();
    }

    @Test
    void nullValuesAreIgnored() {
        assertThat(TROUT.evaluateReading(null, null, null)).isEmpty();
    }

    @Test
    void highTemperatureIsWarningBeforeCriticalLimit() {
        List<Rules.Result> r = TROUT.evaluateReading(17.0, null, null);
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.variable()).isEqualTo("temp_c");
            assertThat(x.level()).isEqualTo("advertencia");
            assertThat(x.message()).isEqualTo("Temperatura alta: 17 °C (óptimo 10–16 °C)");
        });
    }

    @Test
    void lowOxygenIsCritical() {
        List<Rules.Result> r = TROUT.evaluateReading(null, null, 4.0);
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.level()).isEqualTo("critica");
            assertThat(x.message()).isEqualTo("Oxígeno disuelto bajo: 4 mg/L (mínimo 6 mg/L)");
        });
    }

    @Test
    void severalVariablesOutOfRange() {
        List<Rules.Result> r = TROUT.evaluateReading(5.0, 9.5, null);
        assertThat(r).extracting(Rules.Result::variable).containsExactly("temp_c", "ph");
        assertThat(r).extracting(Rules.Result::level).containsOnly("critica");
    }

    @Test
    void feedingRateByWeightAndTemperature() {
        assertThat(TROUT.feedingRatePct(2.5, 12.0)).isEqualTo(4.5);
        assertThat(TROUT.feedingRatePct(0.5, 9.0)).isEqualTo(4.5); // 6 % x 0.75
        assertThat(TROUT.feedingRatePct(100.0, 14.0)).isEqualTo(1.5);
    }

    @Test
    void feedingIsSuspendedAbove18Degrees() {
        assertThat(TROUT.feedingRatePct(10.0, 19.0)).isZero();
        assertThat(TROUT.dailyRationKg(50.0, 10.0, 19.0)).isZero();
    }

    @Test
    void dailyRation() {
        assertThat(TROUT.dailyRationKg(10.0, 30.0, 17.0)).isEqualTo(0.154); // 10 kg x 2.2 % x 0.7
    }

    @Test
    void variableWithoutThresholdsIsNotEvaluated() {
        Rules onlyPh = new Rules(List.of(new Rules.Range("ph", "pH", false, "", 6.5, 8.5, 6.0, 9.0)), List.of(), List.of());
        assertThat(onlyPh.evaluateReading(30.0, 7.0, 1.0)).isEmpty();
        // Without a feeding curve there is no ration, instead of an invented one
        assertThat(onlyPh.feedingRatePct(10.0, 12.0)).isZero();
    }

    @Test
    void rounding() {
        assertThat(Rules.round(12.34567, 2)).isEqualTo(12.35);
        assertThat(Rules.round(0.0005, 3)).isEqualTo(0.001);
    }
}
