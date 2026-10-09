package com.lacocha.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sistema experto (nivel 1 de la IA): umbrales de calidad de agua y ración de alimento.
 *
 * Los valores son de referencia para trucha arcoíris. Antes del piloto hay que ajustarlos con el
 * productor aliado y citarlos de manuales técnicos (AUNAP, FAO) y de la tabla del fabricante del alimento.
 */
public final class Reglas {

    private Reglas() {
    }

    private static final double INF = Double.POSITIVE_INFINITY;

    record Rango(String variable, String nombre, boolean femenino, String unidad,
            double optimoMin, double optimoMax, double criticoMin, double criticoMax) {
    }

    static final List<Rango> RANGOS = List.of(
            new Rango("temp_c", "Temperatura", true, " °C", 10.0, 16.0, 6.0, 18.0),
            new Rango("ph", "pH", false, "", 6.5, 8.5, 6.0, 9.0),
            new Rango("oxigeno_mg_l", "Oxígeno disuelto", false, " mg/L", 6.0, INF, 5.0, INF));

    public record Resultado(String variable, double valor, String nivel, String mensaje) {
    }

    public static List<Resultado> evaluarLectura(Double tempC, Double ph, Double oxigenoMgL) {
        Double[] valores = {tempC, ph, oxigenoMgL};
        List<Resultado> resultados = new ArrayList<>();
        for (int i = 0; i < RANGOS.size(); i++) {
            Rango r = RANGOS.get(i);
            Double valor = valores[i];
            if (valor == null || (valor >= r.optimoMin() && valor <= r.optimoMax())) {
                continue;
            }
            boolean critico = valor < r.criticoMin() || valor > r.criticoMax();
            String direccion = (valor > r.optimoMax() ? "alt" : "baj") + (r.femenino() ? "a" : "o");
            String optimo = r.optimoMax() == INF
                    ? "mínimo " + fmt(r.optimoMin()) + r.unidad()
                    : "óptimo " + fmt(r.optimoMin()) + "–" + fmt(r.optimoMax()) + r.unidad();
            resultados.add(new Resultado(r.variable(), valor, critico ? "critica" : "advertencia",
                    r.nombre() + " " + direccion + ": " + fmt(valor) + r.unidad() + " (" + optimo + ")"));
        }
        return resultados;
    }

    /** % de la biomasa por día según el peso del pez (g), a temperatura óptima. {peso máximo, tasa} */
    private static final double[][] TASA_POR_PESO = {{1.0, 6.0}, {5.0, 4.5}, {20.0, 3.0}, {50.0, 2.2}, {INF, 1.5}};
    /** Factor según la temperatura del agua (°C). Sobre 18 °C se suspende la alimentación. */
    private static final double[][] FACTOR_POR_TEMPERATURA = {{8.0, 0.5}, {10.0, 0.75}, {16.0, 1.0}, {18.0, 0.7}, {INF, 0.0}};

    public static double tasaAlimentacionPct(double pesoG, double tempC) {
        return redondear(buscar(TASA_POR_PESO, pesoG) * buscar(FACTOR_POR_TEMPERATURA, tempC), 2);
    }

    public static double racionDiariaKg(double biomasaKg, double pesoG, double tempC) {
        return redondear(biomasaKg * tasaAlimentacionPct(pesoG, tempC) / 100, 3);
    }

    private static double buscar(double[][] tabla, double x) {
        for (double[] fila : tabla) {
            if (x <= fila[0]) {
                return fila[1];
            }
        }
        throw new IllegalStateException("tabla sin fila final");
    }

    static double redondear(double x, int decimales) {
        double factor = Math.pow(10, decimales);
        return Math.round(x * factor) / factor;
    }

    /** 19.0 -> "19", 6.25 -> "6.25" (siempre con punto, sin importar el idioma del servidor). */
    private static String fmt(double x) {
        return BigDecimal.valueOf(x).stripTrailingZeros().toPlainString();
    }
}
