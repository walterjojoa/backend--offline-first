package com.lacocha.backend.modelo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Catálogo: se puede editar; gana el cambio con actualizadoEn más reciente. */
@Entity
@Table(name = "lotes")
public class Lote {

    @Id
    private UUID id;
    private UUID estanqueId;
    private String codigo;
    private LocalDate fechaSiembra;
    private Integer cantidadInicial;
    @Column(name = "peso_inicial_g")
    private Double pesoInicialG;
    private String estado = "activo"; // activo | cerrado
    private Instant actualizadoEn;
    private Instant servidorEn;

    @PrePersist
    @PreUpdate
    void marcarServidor() {
        servidorEn = Reloj.ahora();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEstanqueId() { return estanqueId; }
    public void setEstanqueId(UUID estanqueId) { this.estanqueId = estanqueId; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public LocalDate getFechaSiembra() { return fechaSiembra; }
    public void setFechaSiembra(LocalDate fechaSiembra) { this.fechaSiembra = fechaSiembra; }
    public Integer getCantidadInicial() { return cantidadInicial; }
    public void setCantidadInicial(Integer cantidadInicial) { this.cantidadInicial = cantidadInicial; }
    public Double getPesoInicialG() { return pesoInicialG; }
    public void setPesoInicialG(Double pesoInicialG) { this.pesoInicialG = pesoInicialG; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(Instant actualizadoEn) { this.actualizadoEn = actualizadoEn; }
    public Instant getServidorEn() { return servidorEn; }
}
