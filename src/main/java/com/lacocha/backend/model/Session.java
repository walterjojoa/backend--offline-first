package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One sign-in that is still alive. A table and not a signed token (JWT) because what really
 * happens on this farm is that a phone gets lost: with a row the session is closed and that is
 * that, while a signed token would keep working until it expires with no way to revoke it.
 */
@Entity
@Table(name = "sesiones")
public class Session {

    @Id
    private UUID id;
    @Column(name = "usuario_id")
    private UUID userId;
    /** SHA-256 of the token, never the token: a copy of the database is not a way in. */
    @Column(name = "token_hash")
    private String tokenHash;
    @Column(name = "dispositivo_id")
    private String deviceId;
    @Column(name = "creada_en")
    private Instant createdAt;
    @Column(name = "expira_en")
    private Instant expiresAt;
    /** Filled in on sign-out. The row stays: it is the log. */
    @Column(name = "cerrada_en")
    private Instant closedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}
