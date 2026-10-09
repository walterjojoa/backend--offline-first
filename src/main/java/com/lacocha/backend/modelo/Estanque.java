package com.lacocha.backend.modelo;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Catálogo: se puede editar; gana el cambio con actualizadoEn más reciente. */
@Entity
@Table(name = "estanques")
public class Estanque {

    @Id
    private UUID id;
    private String nombre;
    private String tipo = "estanque"; // estanque | tanque | jaula
    @Column(name = "volumen_m3")
    private Double volumenM3;
    private boolean activo = true;
    /** Hora de la edición según quien la hizo (celular o panel): decide qué cambio gana. */
    private Instant actualizadoEn;
    /** Hora del servidor al guardar: es el cursor de /api/sync/pull. */
    private Instant servidorEn;

    @PrePersist
    @PreUpdate
    void marcarServidor() {
        servidorEn = Reloj.ahora();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Double getVolumenM3() { return volumenM3; }
    public void setVolumenM3(Double volumenM3) { this.volumenM3 = volumenM3; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
    public Instant getServidorEn() { return servidorEn; }
}
