package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What the AI service found. Kept apart from {@link Alert} on purpose; V26 explains why at length.
 *
 * The short version: an Alert comes from comparing one value against one threshold, and the
 * database can demand that. A Finding can come from the shape of thirty readings, from the slope of
 * a whole window, or from two biometries and the temperature between them.
 *
 * The message is stored and not recomputed. If a threshold is adjusted tomorrow, today's finding
 * has to keep saying what it said when it was raised: that is what lets anyone audit why a decision
 * was taken.
 */
@Entity
@Table(name = "hallazgos_ia")
public class Finding {

    @Id
    private UUID id = UUID.randomUUID();
    /** One of the two, never both: water findings belong to a pond, batch findings to a batch. */
    @Column(name = "estanque_id")
    private UUID pondId;
    @Column(name = "lote_id")
    private UUID batchId;
    /** desviacion | tendencia | combinacion | atipica | crecimiento | mortalidad */
    @Column(name = "clase")
    private String kind;
    /** informe | advertencia | critica, the same words the expert system uses. */
    @Column(name = "nivel")
    private String level;
    /** The water variable when the finding is about a single one; null when it looks at several. */
    private String variable;
    @Column(name = "puntaje")
    private double score;
    @Column(name = "mensaje")
    private String message;
    @Column(name = "medido_en")
    private Instant measuredAt;
    @Column(name = "creado_en")
    private Instant createdAt = ServerClock.now();
    @Column(name = "atendido")
    private boolean attended = false;
    @Column(name = "atendido_en")
    private Instant attendedAt;
    @Column(name = "atendido_por")
    private String attendedBy;

    public UUID getId() { return id; }
    public UUID getPondId() { return pondId; }
    public void setPondId(UUID pondId) { this.pondId = pondId; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getVariable() { return variable; }
    public void setVariable(String variable) { this.variable = variable; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
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
