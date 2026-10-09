package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mortalidades")
public class Mortality extends Event {

    @Column(name = "lote_id")
    private UUID batchId;
    @Column(name = "cantidad")
    private Integer quantity;
    @Column(name = "causa")
    private String cause;

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
}
