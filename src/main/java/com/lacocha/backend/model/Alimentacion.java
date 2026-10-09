package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "alimentaciones")
public class Alimentacion extends Evento {

    private UUID loteId;
    private Double kg;

    public UUID getLoteId() { return loteId; }
    public void setLoteId(UUID loteId) { this.loteId = loteId; }
    public Double getKg() { return kg; }
    public void setKg(Double kg) { this.kg = kg; }
}
