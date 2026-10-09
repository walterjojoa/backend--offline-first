package com.lacocha.backend.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "lecturas_agua")
public class WaterReading extends Event {

    @Column(name = "estanque_id")
    private UUID pondId;
    @Column(name = "temp_c")
    private Double tempC;
    private Double ph;
    @Column(name = "oxigeno_mg_l")
    private Double oxygenMgL;
    /** Raw voltage of the pH probe, useful for recalibration. */
    private Double mv;

    public UUID getPondId() { return pondId; }
    public void setPondId(UUID pondId) { this.pondId = pondId; }
    public Double getTempC() { return tempC; }
    public void setTempC(Double tempC) { this.tempC = tempC; }
    public Double getPh() { return ph; }
    public void setPh(Double ph) { this.ph = ph; }
    public Double getOxygenMgL() { return oxygenMgL; }
    public void setOxygenMgL(Double oxygenMgL) { this.oxygenMgL = oxygenMgL; }
    public Double getMv() { return mv; }
    public void setMv(Double mv) { this.mv = mv; }
}
