package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Fry count, from the infrared counter or entered by hand. */
@Entity
@Table(name = "conteos")
public class FryCount extends Event {

    @Column(name = "lote_id")
    private UUID batchId;
    private Integer total;
    /** Beam interruptions long enough to be two fry passing together. */
    @Column(name = "cortes_multiples")
    private Integer multipleCuts;

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public Integer getMultipleCuts() { return multipleCuts; }
    public void setMultipleCuts(Integer multipleCuts) { this.multipleCuts = multipleCuts; }
}
