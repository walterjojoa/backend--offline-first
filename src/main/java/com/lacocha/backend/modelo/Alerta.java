package com.lacocha.backend.modelo;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** La genera el servidor al recibir una lectura fuera de rango. */
@Entity
@Table(name = "alertas")
public class Alerta {

    @Id
    private UUID id = UUID.randomUUID();
    private UUID estanqueId;
    private UUID lecturaId;
    private String variable;
    private Double valor;
    private String nivel; // advertencia | critica
    private String mensaje;
    private Instant medidoEn;
    private Instant creadaEn = Reloj.ahora();
    private boolean atendida = false;
    private Instant atendidaEn;

    public UUID getId() { return id; }
    public UUID getEstanqueId() { return estanqueId; }
    public void setEstanqueId(UUID estanqueId) { this.estanqueId = estanqueId; }
    public UUID getLecturaId() { return lecturaId; }
    public void setLecturaId(UUID lecturaId) { this.lecturaId = lecturaId; }
    public String getVariable() { return variable; }
    public void setVariable(String variable) { this.variable = variable; }
    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    public Instant getMedidoEn() { return medidoEn; }
    public void setMedidoEn(Instant medidoEn) { this.medidoEn = medidoEn; }
    public Instant getCreadaEn() { return creadaEn; }
    public boolean isAtendida() { return atendida; }
    public void setAtendida(boolean atendida) { this.atendida = atendida; }
    public Instant getAtendidaEn() { return atendidaEn; }
    public void setAtendidaEn(Instant atendidaEn) { this.atendidaEn = atendidaEn; }
}
