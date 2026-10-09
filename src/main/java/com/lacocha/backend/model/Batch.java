package com.lacocha.backend.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Batch of fry in a pond. Catalog: can be edited; the change with the latest updatedAt wins. */
@Entity
@Table(name = "lotes")
public class Batch {

    @Id
    private UUID id;
    @Column(name = "estanque_id")
    private UUID pondId;
    @Column(name = "codigo")
    private String code;
    @Column(name = "fecha_siembra")
    private LocalDate stockingDate;
    @Column(name = "cantidad_inicial")
    private Integer initialQuantity;
    @Column(name = "peso_inicial_g")
    private Double initialWeightG;
    @Column(name = "estado")
    private String status = "activo"; // activo | cerrado
    @Column(name = "actualizado_en")
    private Instant updatedAt;
    @Column(name = "servidor_en")
    private Instant serverTime;

    @PrePersist
    @PreUpdate
    void markServerTime() {
        serverTime = ServerClock.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPondId() { return pondId; }
    public void setPondId(UUID pondId) { this.pondId = pondId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public LocalDate getStockingDate() { return stockingDate; }
    public void setStockingDate(LocalDate stockingDate) { this.stockingDate = stockingDate; }
    public Integer getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(Integer initialQuantity) { this.initialQuantity = initialQuantity; }
    public Double getInitialWeightG() { return initialWeightG; }
    public void setInitialWeightG(Double initialWeightG) { this.initialWeightG = initialWeightG; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getServerTime() { return serverTime; }
}
