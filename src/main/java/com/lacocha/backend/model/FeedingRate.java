package com.lacocha.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** % of the biomass fed per day, by fish weight. Comes from the feed manufacturer's table. */
@Entity
@Table(name = "tasas_alimentacion")
public class FeedingRate {

    /** Rows are evaluated in this order. */
    @Id
    @Column(name = "orden")
    private Integer position;
    /** Up to this weight (g). null in the last row: from there up. */
    @Column(name = "peso_hasta_g")
    private Double upToWeightG;
    @Column(name = "tasa_pct")
    private Double ratePct;
    @Column(name = "fuente")
    private String source;

    public Integer getPosition() { return position; }
    public Double getUpToWeightG() { return upToWeightG; }
    public Double getRatePct() { return ratePct; }
    public String getSource() { return source; }
}
