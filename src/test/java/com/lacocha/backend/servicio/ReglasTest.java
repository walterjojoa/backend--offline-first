package com.lacocha.backend.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReglasTest {

    @Test
    void lecturaEnRangoNoGeneraAlertas() {
        assertThat(Reglas.evaluarLectura(12.0, 7.2, 8.0)).isEmpty();
    }

    @Test
    void valoresNulosSeIgnoran() {
        assertThat(Reglas.evaluarLectura(null, null, null)).isEmpty();
    }

    @Test
    void temperaturaAltaEsAdvertenciaAntesDelLimiteCritico() {
        List<Reglas.Resultado> r = Reglas.evaluarLectura(17.0, null, null);
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.variable()).isEqualTo("temp_c");
            assertThat(x.nivel()).isEqualTo("advertencia");
            assertThat(x.mensaje()).isEqualTo("Temperatura alta: 17 °C (óptimo 10–16 °C)");
        });
    }

    @Test
    void oxigenoBajoEsCritico() {
        List<Reglas.Resultado> r = Reglas.evaluarLectura(null, null, 4.0);
        assertThat(r).singleElement().satisfies(x -> {
            assertThat(x.nivel()).isEqualTo("critica");
            assertThat(x.mensaje()).isEqualTo("Oxígeno disuelto bajo: 4 mg/L (mínimo 6 mg/L)");
        });
    }

    @Test
    void variasVariablesFueraDeRango() {
        List<Reglas.Resultado> r = Reglas.evaluarLectura(5.0, 9.5, null);
        assertThat(r).extracting(Reglas.Resultado::variable).containsExactly("temp_c", "ph");
        assertThat(r).extracting(Reglas.Resultado::nivel).containsOnly("critica");
    }

    @Test
    void tasaDeAlimentacionSegunPesoYTemperatura() {
        assertThat(Reglas.tasaAlimentacionPct(2.5, 12.0)).isEqualTo(4.5);
        assertThat(Reglas.tasaAlimentacionPct(0.5, 9.0)).isEqualTo(4.5); // 6 % x 0.75
        assertThat(Reglas.tasaAlimentacionPct(100.0, 14.0)).isEqualTo(1.5);
    }

    @Test
    void sobre18GradosSeSuspendeLaAlimentacion() {
        assertThat(Reglas.tasaAlimentacionPct(10.0, 19.0)).isZero();
        assertThat(Reglas.racionDiariaKg(50.0, 10.0, 19.0)).isZero();
    }

    @Test
    void racionDiaria() {
        assertThat(Reglas.racionDiariaKg(10.0, 30.0, 17.0)).isEqualTo(0.154); // 10 kg x 2.2 % x 0.7
    }

    @Test
    void redondear() {
        assertThat(Reglas.redondear(12.34567, 2)).isEqualTo(12.35);
        assertThat(Reglas.redondear(0.0005, 3)).isEqualTo(0.001);
    }
}
