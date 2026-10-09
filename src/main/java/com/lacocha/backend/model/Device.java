package com.lacocha.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A phone or ESP32 node that syncs. It is created by its first push; every event points to it
 * (foreign key), so it is never deleted: a lost phone is deactivated instead.
 */
@Entity
@Table(name = "dispositivos")
public class Device {

    /** The same text the phone sends as dispositivo_id ("cel-cuidador-1"). */
    @Id
    private String id;
    /** Whose phone it is, so the producer can tell them apart ("celular de Don Luis"). */
    @Column(name = "descripcion")
    private String description;
    /** false = deactivated: it can no longer sync. */
    @Column(name = "activo")
    private boolean active = true;
    @Column(name = "primer_visto_en")
    private Instant firstSeenAt;
    /** Updated on every push: tells a phone without signal apart from a lost one. */
    @Column(name = "ultimo_visto_en")
    private Instant lastSeenAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
