package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "lecturas_agua")
public class LecturaAgua extends Evento {

    private UUID estanqueId;
    @Column(name = "temp_c")
    private Double tempC;
    private Double ph;
    @Column(name = "oxigeno_mg_l")
    private Double oxigenoMgL;
    /** Voltaje crudo de la sonda de pH, útil para recalibrar. */
    private Double mv;

    public UUID getEstanqueId() { return estanqueId; }
    public void setEstanqueId(UUID estanqueId) { this.estanqueId = estanqueId; }
    public Double getTempC() { return tempC; }
    public void setTempC(Double tempC) { this.tempC = tempC; }
    public Double getPh() { return ph; }
    public void setPh(Double ph) { this.ph = ph; }
    public Double getOxigenoMgL() { return oxigenoMgL; }
    public void setOxigenoMgL(Double oxigenoMgL) { this.oxigenoMgL = oxigenoMgL; }
    public Double getMv() { return mv; }
    public void setMv(Double mv) { this.mv = mv; }
}
