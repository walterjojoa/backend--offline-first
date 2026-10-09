package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Created by the server when it receives an out-of-range reading. */
@Entity
@Table(name = "alertas")
public class Alert {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "estanque_id")
    private UUID pondId;
    @Column(name = "lectura_id")
    private UUID readingId;
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

    public UUID getId() { return id; }
    public UUID getPondId() { return pondId; }
    public void setPondId(UUID pondId) { this.pondId = pondId; }
    public UUID getReadingId() { return readingId; }
    public void setReadingId(UUID readingId) { this.readingId = readingId; }
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
}
