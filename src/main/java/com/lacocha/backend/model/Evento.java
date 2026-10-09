package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/** Campos comunes de los eventos. Nunca se editan y el id (UUID) lo genera el celular. */
@MappedSuperclass
public abstract class Evento {

    @Id
    private UUID id;
    private String dispositivoId;
    private String origen; // sensor | contador | voz | manual
    private Instant registradoEn;
    private Instant recibidoEn;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(String dispositivoId) { this.dispositivoId = dispositivoId; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public Instant getRegistradoEn() { return registradoEn; }
    public void setRegistradoEn(Instant registradoEn) { this.registradoEn = registradoEn; }
    public Instant getRecibidoEn() { return recibidoEn; }
    public void setRecibidoEn(Instant recibidoEn) { this.recibidoEn = recibidoEn; }
}
