package com.lacocha.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Water quality thresholds of one variable, with the source they come from. */
@Entity
@Table(name = "parametros_rango")
public class RangeParameter {

    /** temp_c | ph | oxigeno_mg_l */
    @Id
    private String variable;
    /** How the variable is named in the alert message ("Temperatura"). */
    @Column(name = "nombre")
    private String label;
    /** Feminine name: "Temperatura alta" and not "Temperatura alto". */
    @Column(name = "femenino")
    private boolean feminine;
    @Column(name = "unidad")
    private String unit;
    @Column(name = "optimo_min")
    private Double optimalMin;
    /** null = no upper limit (oxygen). */
    @Column(name = "optimo_max")
    private Double optimalMax;
    @Column(name = "critico_min")
    private Double criticalMin;
    @Column(name = "critico_max")
    private Double criticalMax;
    @Column(name = "fuente")
    private String source;
    @Column(name = "actualizado_en")
    private Instant updatedAt;

    public String getVariable() { return variable; }
    public String getLabel() { return label; }
    public boolean isFeminine() { return feminine; }
    public String getUnit() { return unit; }
    public Double getOptimalMin() { return optimalMin; }
    public Double getOptimalMax() { return optimalMax; }
    public Double getCriticalMin() { return criticalMin; }
    public Double getCriticalMax() { return criticalMax; }
    public String getSource() { return source; }
    public Instant getUpdatedAt() { return updatedAt; }
}
