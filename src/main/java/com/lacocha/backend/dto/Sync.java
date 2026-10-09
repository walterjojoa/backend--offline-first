package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.lacocha.backend.dto.Catalogo.EstanqueSalida;
import com.lacocha.backend.dto.Catalogo.EstanqueSync;
import com.lacocha.backend.dto.Catalogo.LoteSalida;
import com.lacocha.backend.dto.Catalogo.LoteSync;
import com.lacocha.backend.model.Alert;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class Sync {

    private Sync() {
    }

    public record PushPeticion(
            // Letras, números, guion, guion bajo y punto: el id queda limpio en los registros y en el log
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "solo letras, números, punto, guion y guion bajo") String dispositivoId,
            @Valid @Size(max = 200) List<EstanqueSync> estanques,
            @Valid @Size(max = 200) List<LoteSync> lotes,
            // Se validan uno por uno en el servicio: un evento malo no debe bloquear la cola del celular
            @Size(max = 500) List<JsonNode> eventos) {
    }

    public record Rechazo(String id, String error) {
    }

    public record PushRespuesta(
            List<UUID> aceptados,
            List<UUID> duplicados,
            List<UUID> obsoletos,
            List<Rechazo> rechazados,
            int alertasGeneradas,
            Instant servidorEn) {
    }

    public record AlertaSalida(UUID id, UUID estanqueId, UUID lecturaId, String variable, Double valor,
            String nivel, String mensaje, Instant medidoEn, boolean atendida, Instant atendidaEn) {

        public static AlertaSalida de(Alert a) {
            return new AlertaSalida(a.getId(), a.getPondId(), a.getReadingId(), a.getVariable(), a.getValue(),
                    a.getLevel(), a.getMessage(), a.getMeasuredAt(), a.isAttended(), a.getAttendedAt());
        }
    }

    public record PullRespuesta(
            List<EstanqueSalida> estanques,
            List<LoteSalida> lotes,
            List<AlertaSalida> alertasPendientes,
            Instant servidorEn) {
    }
}
