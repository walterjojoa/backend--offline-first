package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "conteos")
public class Conteo extends Evento {

    private UUID loteId;
    private Integer total;
    private Integer cortesMultiples;

    public UUID getLoteId() { return loteId; }
    public void setLoteId(UUID loteId) { this.loteId = loteId; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public Integer getCortesMultiples() { return cortesMultiples; }
    public void setCortesMultiples(Integer cortesMultiples) { this.cortesMultiples = cortesMultiples; }
}
