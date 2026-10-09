package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "biometrias")
public class Biometria extends Evento {

    private UUID loteId;
    @Column(name = "peso_promedio_g")
    private Double pesoPromedioG;
    private Integer muestra;

    public UUID getLoteId() { return loteId; }
    public void setLoteId(UUID loteId) { this.loteId = loteId; }
    public Double getPesoPromedioG() { return pesoPromedioG; }
    public void setPesoPromedioG(Double pesoPromedioG) { this.pesoPromedioG = pesoPromedioG; }
    public Integer getMuestra() { return muestra; }
    public void setMuestra(Integer muestra) { this.muestra = muestra; }
}
