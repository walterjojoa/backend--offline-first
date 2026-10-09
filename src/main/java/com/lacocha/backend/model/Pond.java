package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Catalog: can be edited; the change with the latest updatedAt wins. */
@Entity
@Table(name = "estanques")
public class Pond {

    @Id
    private UUID id;
    @Column(name = "nombre")
    private String name;
    @Column(name = "tipo")
    private String type = "estanque"; // estanque | tanque | jaula
    @Column(name = "volumen_m3")
    private Double volumeM3;
    @Column(name = "activo")
    private boolean active = true;
    /** Time of the edit according to whoever made it (phone or panel): decides which change wins. */
    @Column(name = "actualizado_en")
    private Instant updatedAt;
    /** Server time when saved: it is the /api/sync/pull cursor. */
    @Column(name = "servidor_en")
    private Instant serverTime;

    @PrePersist
    @PreUpdate
    void markServerTime() {
        serverTime = ServerClock.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getVolumeM3() { return volumeM3; }
    public void setVolumeM3(Double volumeM3) { this.volumeM3 = volumeM3; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getServerTime() { return serverTime; }
}
