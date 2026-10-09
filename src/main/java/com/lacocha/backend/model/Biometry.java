package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Average weight measured on a sample of the batch. */
@Entity
@Table(name = "biometrias")
public class Biometry extends Event {

    @Column(name = "lote_id")
    private UUID batchId;
    @Column(name = "peso_promedio_g")
    private Double avgWeightG;
    @Column(name = "muestra")
    private Integer sampleSize;

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public Double getAvgWeightG() { return avgWeightG; }
    public void setAvgWeightG(Double avgWeightG) { this.avgWeightG = avgWeightG; }
    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }
}
