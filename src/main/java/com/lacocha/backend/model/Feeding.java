package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "alimentaciones")
public class Feeding extends Event {

    @Column(name = "lote_id")
    private UUID batchId;
    private Double kg;

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public Double getKg() { return kg; }
    public void setKg(Double kg) { this.kg = kg; }
}
