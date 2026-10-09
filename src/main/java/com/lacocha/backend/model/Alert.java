package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Created by the server. Today they come from an out-of-range water reading, but the table already
 * allows batch alerts (mortality, survival): those have no reading or variable, and carry the batch.
 */
@Entity
@Table(name = "alertas")
public class Alert {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "estanque_id")
    private UUID pondId;
    @Column(name = "lectura_id")
    private UUID readingId;
    @Column(name = "lote_id")
    private UUID batchId;
    /** lectura_agua | mortalidad | supervivencia */
    @Column(name = "disparada_por")
    private String triggeredBy = "lectura_agua";
    private String variable;
    @Column(name = "valor")
    private Double value;
    @Column(name = "nivel")
    private String level; // advertencia | critica
    @Column(name = "mensaje")
    private String message;
    @Column(name = "medido_en")
    private Instant measuredAt;
    @Column(name = "creada_en")
    private Instant createdAt = ServerClock.now();
    @Column(name = "atendida")
    private boolean attended = false;
    @Column(name = "atendida_en")
    private Instant attendedAt;
    /** Device id when the phone attended it, or "panel" when it was the browser. */
    @Column(name = "atendida_por")
    private String attendedBy;

    public UUID getId() { return id; }
    public UUID getPondId() { return pondId; }
    public void setPondId(UUID pondId) { this.pondId = pondId; }
    public UUID getReadingId() { return readingId; }
    public void setReadingId(UUID readingId) { this.readingId = readingId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public String getVariable() { return variable; }
    public void setVariable(String variable) { this.variable = variable; }
    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(Instant measuredAt) { this.measuredAt = measuredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isAttended() { return attended; }
    public void setAttended(boolean attended) { this.attended = attended; }
    public Instant getAttendedAt() { return attendedAt; }
    public void setAttendedAt(Instant attendedAt) { this.attendedAt = attendedAt; }
    public String getAttendedBy() { return attendedBy; }
    public void setAttendedBy(String attendedBy) { this.attendedBy = attendedBy; }
}
