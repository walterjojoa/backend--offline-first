package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A person who signs in. The device is the gadget and the user is the person: on the farm the
 * phones get lent around, so the two are kept apart and a session points at both.
 */
@Entity
@Table(name = "usuarios")
public class User {

    @Id
    private UUID id;
    /** The id number, which is what people on the farm know by heart. */
    @Column(name = "documento")
    private String document;
    @Column(name = "nombre")
    private String name;
    /** BCrypt. The plain password never reaches the database. */
    @Column(name = "clave_hash")
    private String passwordHash;
    /** cuidador = records in the field. productor = also sees the panel. */
    @Column(name = "rol")
    private String role = "cuidador";
    /** false = cannot sign in. Never deleted: their records still point here. */
    @Column(name = "activo")
    private boolean active = true;
    @Column(name = "creado_en")
    private Instant createdAt;
    @Column(name = "ultimo_ingreso_en")
    private Instant lastLoginAt;
    /** true while the password is a temporary one the producer handed over: it only opens the door. */
    @Column(name = "debe_cambiar_clave")
    private boolean mustChangePassword = false;
    @Column(name = "clave_reiniciada_en")
    private Instant passwordResetAt;
    /** Who reset it. Signing in as someone else has to be written down somewhere. */
    @Column(name = "clave_reiniciada_por")
    private UUID passwordResetBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDocument() { return document; }
    public void setDocument(String document) { this.document = document; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public Instant getPasswordResetAt() { return passwordResetAt; }
    public void setPasswordResetAt(Instant passwordResetAt) { this.passwordResetAt = passwordResetAt; }
    public UUID getPasswordResetBy() { return passwordResetBy; }
    public void setPasswordResetBy(UUID passwordResetBy) { this.passwordResetBy = passwordResetBy; }

    public boolean isProducer() { return "productor".equals(role); }
}
