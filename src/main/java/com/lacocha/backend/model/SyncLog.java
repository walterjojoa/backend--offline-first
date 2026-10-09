package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What happened in one push: the same counts the phone receives, kept on the server. It is the
 * evidence that offline-first works (duplicates = safe retries, rejections that did not block the queue).
 */
@Entity
@Table(name = "sincronizaciones")
public class SyncLog {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "dispositivo_id")
    private String deviceId;
    @Column(name = "aceptados")
    private int accepted;
    @Column(name = "duplicados")
    private int duplicates;
    @Column(name = "obsoletos")
    private int stale;
    @Column(name = "rechazados")
    private int rejected;
    @Column(name = "alertas_generadas")
    private int alertsCreated;
    @Column(name = "servidor_en")
    private Instant serverTime;

    public UUID getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public int getAccepted() { return accepted; }
    public void setAccepted(int accepted) { this.accepted = accepted; }
    public int getDuplicates() { return duplicates; }
    public void setDuplicates(int duplicates) { this.duplicates = duplicates; }
    public int getStale() { return stale; }
    public void setStale(int stale) { this.stale = stale; }
    public int getRejected() { return rejected; }
    public void setRejected(int rejected) { this.rejected = rejected; }
    public int getAlertsCreated() { return alertsCreated; }
    public void setAlertsCreated(int alertsCreated) { this.alertsCreated = alertsCreated; }
    public Instant getServerTime() { return serverTime; }
    public void setServerTime(Instant serverTime) { this.serverTime = serverTime; }
}
