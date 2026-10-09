package com.lacocha.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Expert system (AI level 1): water quality thresholds and feed ration.
 *
 * The numbers are not compiled here: they come from the parametros_rango, tasas_alimentacion and
 * factores_temperatura tables, each with its source (see {@link RuleParameters}). Adjusting them with
 * the producer is an UPDATE, not a redeploy. An instance is immutable, so it can be shared between threads.
 */
public final class Rules {

    private static final double INF = Double.POSITIVE_INFINITY;

    /**
     * Acceptable range of one variable. label, feminine and unit are used to build the alert message
     * in Spanish ("Temperatura alta", "pH bajo"). A missing maximum is stored as infinity.
     */
    public record Range(String variable, String label, boolean feminine, String unit,
            double optimalMin, double optimalMax, double criticalMin, double criticalMax) {
    }

    /** One row of a curve: applies up to "upTo" (inclusive) and is worth "value". */
    public record Step(double upTo, double value) {
    }

    /** level is "advertencia" or "critica"; message is shown to the caretaker. */
    public record Result(String variable, double value, String level, String message) {
    }

    /** Order in which the variables are evaluated, which is the order of the alerts. */
    private static final List<String> VARIABLES = List.of("temp_c", "ph", "oxigeno_mg_l");

    private final List<Range> ranges;
    /** % of biomass per day by fish weight (g), at optimal temperature. */
    private final List<Step> rateByWeight;
    /** Factor by water temperature (°C). 0 suspends feeding. */
    private final List<Step> factorByTemperature;

    public Rules(List<Range> ranges, List<Step> rateByWeight, List<Step> factorByTemperature) {
        this.ranges = List.copyOf(ranges);
        this.rateByWeight = List.copyOf(rateByWeight);
        this.factorByTemperature = List.copyOf(factorByTemperature);
    }

    /** A missing maximum (NULL in the database) means "no upper limit". */
    static double orInfinity(Double value) {
        return value != null ? value : INF;
    }

    public List<Result> evaluateReading(Double tempC, Double ph, Double oxygenMgL) {
        Double[] values = {tempC, ph, oxygenMgL};
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < VARIABLES.size(); i++) {
            // A variable without a row is not evaluated: deleting the row switches its alerts off
            Range r = range(VARIABLES.get(i));
            Double value = values[i];
            if (r == null || value == null || (value >= r.optimalMin() && value <= r.optimalMax())) {
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

    private Range range(String variable) {
        return ranges.stream().filter(r -> r.variable().equals(variable)).findFirst().orElse(null);
    }

    public double feedingRatePct(double weightG, double tempC) {
        return round(lookup(rateByWeight, weightG) * lookup(factorByTemperature, tempC), 2);
    }

    public double dailyRationKg(double biomassKg, double weightG, double tempC) {
        return round(biomassKg * feedingRatePct(weightG, tempC) / 100, 3);
    }

    private static double lookup(List<Step> curve, double x) {
        for (Step step : curve) {
            if (x <= step.upTo()) {
                return step.value();
            }
        }
        // Empty curve, or someone gave the last row a limit: better no ration than an invented one
        return 0;
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
