package com.lacocha.backend.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.lacocha.backend.modelo.Estanque;
import com.lacocha.backend.modelo.Lote;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Estanques y lotes: lo que se crea desde el panel, lo que sube el celular y lo que se devuelve. */
public final class Catalogo {

    private Catalogo() {
    }

    static final String TIPOS_ESTANQUE = "estanque|tanque|jaula";
    static final String ESTADOS_LOTE = "activo|cerrado";

    public record EstanqueCrear(
            UUID id,
            @NotBlank @Size(max = 80) String nombre,
            @Pattern(regexp = TIPOS_ESTANQUE) String tipo,
            @Positive Double volumenM3) {
    }

    public record EstanqueEditar(
            @Size(min = 1, max = 80) String nombre,
            @Pattern(regexp = TIPOS_ESTANQUE) String tipo,
            @Positive Double volumenM3,
            Boolean activo) {
    }

    public record EstanqueSync(
            @NotNull UUID id,
            @NotBlank @Size(max = 80) String nombre,
            @Pattern(regexp = TIPOS_ESTANQUE) String tipo,
            @Positive Double volumenM3,
            Boolean activo,
            @NotNull OffsetDateTime actualizadoEn) {
    }

    public record EstanqueSalida(UUID id, String nombre, String tipo, Double volumenM3, boolean activo,
            Instant actualizadoEn) {

        public static EstanqueSalida de(Estanque e) {
            return new EstanqueSalida(e.getId(), e.getNombre(), e.getTipo(), e.getVolumenM3(), e.isActivo(),
                    e.getActualizadoEn());
        }
    }

    public record LoteCrear(
            UUID id,
            @NotNull UUID estanqueId,
            @NotBlank @Size(max = 40) String codigo,
            @PastOrPresent LocalDate fechaSiembra,
            @PositiveOrZero Integer cantidadInicial,
            @Positive Double pesoInicialG,
            @Pattern(regexp = ESTADOS_LOTE) String estado) {
    }

    public record LoteEditar(
            @Size(min = 1, max = 40) String codigo,
            @PastOrPresent LocalDate fechaSiembra,
            @PositiveOrZero Integer cantidadInicial,
            @Positive Double pesoInicialG,
            @Pattern(regexp = ESTADOS_LOTE) String estado) {
    }

    public record LoteSync(
            @NotNull UUID id,
            @NotNull UUID estanqueId,
            @NotBlank @Size(max = 40) String codigo,
            @PastOrPresent LocalDate fechaSiembra,
            @PositiveOrZero Integer cantidadInicial,
            @Positive Double pesoInicialG,
            @Pattern(regexp = ESTADOS_LOTE) String estado,
            @NotNull OffsetDateTime actualizadoEn) {
    }

    public record LoteSalida(UUID id, UUID estanqueId, String codigo, LocalDate fechaSiembra,
            Integer cantidadInicial, Double pesoInicialG, String estado, Instant actualizadoEn) {

        public static LoteSalida de(Lote l) {
            return new LoteSalida(l.getId(), l.getEstanqueId(), l.getCodigo(), l.getFechaSiembra(),
                    l.getCantidadInicial(), l.getPesoInicialG(), l.getEstado(), l.getActualizadoEn());
        }
    }
}
