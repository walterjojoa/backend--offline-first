package com.lacocha.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Correction of the feeding rate by water temperature: 1.0 = full ration, 0.0 = no feeding. */
@Entity
@Table(name = "factores_temperatura")
public class TemperatureFactor {

    @Id
    @Column(name = "orden")
    private Integer position;
    /** Up to this temperature (°C). null in the last row: from there up. */
    @Column(name = "temp_hasta_c")
    private Double upToTempC;
    private Double factor;
    @Column(name = "fuente")
    private String source;

    public Integer getPosition() { return position; }
    public Double getUpToTempC() { return upToTempC; }
    public Double getFactor() { return factor; }
    public String getSource() { return source; }
}
