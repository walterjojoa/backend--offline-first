package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "mortalidades")
public class Mortalidad extends Evento {

    private UUID loteId;
    private Integer cantidad;
    private String causa;

    public UUID getLoteId() { return loteId; }
    public void setLoteId(UUID loteId) { this.loteId = loteId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getCausa() { return causa; }
    public void setCausa(String causa) { this.causa = causa; }
}
