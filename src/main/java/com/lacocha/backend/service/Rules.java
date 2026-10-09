package com.lacocha.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Expert system (AI level 1): water quality thresholds and feed ration.
 *
 * The values are references for rainbow trout. Before the pilot they must be adjusted with the partner
 * producer and cited from technical manuals (AUNAP, FAO) and from the feed manufacturer's table.
 */
public final class Rules {

    private Rules() {
    }

    private static final double INF = Double.POSITIVE_INFINITY;

    /**
     * Acceptable range of one variable. label, feminine and unit are used to build the alert message
     * in Spanish ("Temperatura alta", "pH bajo").
     */
    record Range(String variable, String label, boolean feminine, String unit,
            double optimalMin, double optimalMax, double criticalMin, double criticalMax) {
    }

    static final List<Range> RANGES = List.of(
            new Range("temp_c", "Temperatura", true, " °C", 10.0, 16.0, 6.0, 18.0),
            new Range("ph", "pH", false, "", 6.5, 8.5, 6.0, 9.0),
            new Range("oxigeno_mg_l", "Oxígeno disuelto", false, " mg/L", 6.0, INF, 5.0, INF));

    /** level is "advertencia" or "critica"; message is shown to the caretaker. */
    public record Result(String variable, double value, String level, String message) {
    }

    public static List<Result> evaluateReading(Double tempC, Double ph, Double oxygenMgL) {
        Double[] values = {tempC, ph, oxygenMgL};
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < RANGES.size(); i++) {
            Range r = RANGES.get(i);
            Double value = values[i];
            if (value == null || (value >= r.optimalMin() && value <= r.optimalMax())) {
                continue;
            }
            boolean critical = value < r.criticalMin() || value > r.criticalMax();
            String direction = (value > r.optimalMax() ? "alt" : "baj") + (r.feminine() ? "a" : "o");
            String optimal = r.optimalMax() == INF
                    ? "mínimo " + fmt(r.optimalMin()) + r.unit()
                    : "óptimo " + fmt(r.optimalMin()) + "–" + fmt(r.optimalMax()) + r.unit();
            results.add(new Result(r.variable(), value, critical ? "critica" : "advertencia",
                    r.label() + " " + direction + ": " + fmt(value) + r.unit() + " (" + optimal + ")"));
        }
        return results;
    }

    /** % of biomass per day by fish weight (g), at optimal temperature. {max weight, rate} */
    private static final double[][] RATE_BY_WEIGHT = {{1.0, 6.0}, {5.0, 4.5}, {20.0, 3.0}, {50.0, 2.2}, {INF, 1.5}};
    /** Factor by water temperature (°C). Above 18 °C feeding is suspended. */
    private static final double[][] FACTOR_BY_TEMPERATURE = {{8.0, 0.5}, {10.0, 0.75}, {16.0, 1.0}, {18.0, 0.7}, {INF, 0.0}};

    public static double feedingRatePct(double weightG, double tempC) {
        return round(lookup(RATE_BY_WEIGHT, weightG) * lookup(FACTOR_BY_TEMPERATURE, tempC), 2);
    }

    public static double dailyRationKg(double biomassKg, double weightG, double tempC) {
        return round(biomassKg * feedingRatePct(weightG, tempC) / 100, 3);
    }

    private static double lookup(double[][] table, double x) {
        for (double[] row : table) {
            if (x <= row[0]) {
                return row[1];
            }
        }
        throw new IllegalStateException("table without a final row");
    }

    static double round(double x, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(x * factor) / factor;
    }

    /** 19.0 -> "19", 6.25 -> "6.25" (always with a dot, whatever the server language). */
    private static String fmt(double x) {
        return BigDecimal.valueOf(x).stripTrailingZeros().toPlainString();
    }
}
