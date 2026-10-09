package com.lacocha.backend.dto;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.lacocha.backend.model.Alimentacion;
import com.lacocha.backend.model.Biometria;
import com.lacocha.backend.model.Conteo;
import com.lacocha.backend.model.Evento;
import com.lacocha.backend.model.LecturaAgua;
import com.lacocha.backend.model.Mortalidad;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Eventos que sube el celular. El campo "tipo" del JSON decide cuál de estos records se usa. */
public final class Eventos {

    private Eventos() {
    }

    static final String ORIGENES = "sensor|contador|voz|manual";

    public sealed interface Entrada permits LecturaAguaEntrada, ConteoEntrada, MortalidadEntrada,
            AlimentacionEntrada, BiometriaEntrada {

        UUID id();

        OffsetDateTime registradoEn();

        String origen();

        /** Estanque (lecturas) o lote (los demás) al que pertenece el evento. */
        UUID padreId();

        boolean padreEsEstanque();

        Evento aEntidad();

        /** Reglas que no caben en una anotación. Devuelve null si todo está bien. */
        default String errorExtra() {
            return null;
        }
    }

    /** tipo del JSON -> record. LinkedHashMap para que el mensaje de error salga en orden. */
    public static final Map<String, Class<? extends Entrada>> TIPOS = new LinkedHashMap<>();

    static {
        TIPOS.put("lectura_agua", LecturaAguaEntrada.class);
        TIPOS.put("conteo", ConteoEntrada.class);
        TIPOS.put("mortalidad", MortalidadEntrada.class);
        TIPOS.put("alimentacion", AlimentacionEntrada.class);
        TIPOS.put("biometria", BiometriaEntrada.class);
    }

    public record LecturaAguaEntrada(
            @NotNull UUID id,
            @NotNull OffsetDateTime registradoEn,
            @Pattern(regexp = ORIGENES) String origen,
            @NotNull UUID estanqueId,
            @DecimalMin("-5") @DecimalMax("40") Double tempC,
            @DecimalMin("0") @DecimalMax("14") Double ph,
            @DecimalMin("0") @DecimalMax("30") Double oxigenoMgL,
            Double mv) implements Entrada {

        public UUID padreId() { return estanqueId; }

        public boolean padreEsEstanque() { return true; }

        @Override
        public String errorExtra() {
            return tempC == null && ph == null && oxigenoMgL == null
                    ? "la lectura no trae temperatura, pH ni oxígeno"
                    : null;
        }

        public Evento aEntidad() {
            LecturaAgua l = new LecturaAgua();
            l.setEstanqueId(estanqueId);
            l.setTempC(tempC);
            l.setPh(ph);
            l.setOxigenoMgL(oxigenoMgL);
            l.setMv(mv);
            return l;
        }
    }

    public record ConteoEntrada(
            @NotNull UUID id,
            @NotNull OffsetDateTime registradoEn,
            @Pattern(regexp = ORIGENES) String origen,
            @NotNull UUID loteId,
            @NotNull @Min(0) Integer total,
            @Min(0) Integer cortesMultiples) implements Entrada {

        public UUID padreId() { return loteId; }

        public boolean padreEsEstanque() { return false; }

        public Evento aEntidad() {
            Conteo c = new Conteo();
            c.setLoteId(loteId);
            c.setTotal(total);
            c.setCortesMultiples(cortesMultiples);
            return c;
        }
    }

    public record MortalidadEntrada(
            @NotNull UUID id,
            @NotNull OffsetDateTime registradoEn,
            @Pattern(regexp = ORIGENES) String origen,
            @NotNull UUID loteId,
            @NotNull @Min(1) Integer cantidad,
            @Size(max = 120) String causa) implements Entrada {

        public UUID padreId() { return loteId; }

        public boolean padreEsEstanque() { return false; }

        public Evento aEntidad() {
            Mortalidad m = new Mortalidad();
            m.setLoteId(loteId);
            m.setCantidad(cantidad);
            m.setCausa(causa);
            return m;
        }
    }

    public record AlimentacionEntrada(
            @NotNull UUID id,
            @NotNull OffsetDateTime registradoEn,
            @Pattern(regexp = ORIGENES) String origen,
            @NotNull UUID loteId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") Double kg) implements Entrada {

        public UUID padreId() { return loteId; }

        public boolean padreEsEstanque() { return false; }

        public Evento aEntidad() {
            Alimentacion a = new Alimentacion();
            a.setLoteId(loteId);
            a.setKg(kg);
            return a;
        }
    }

    public record BiometriaEntrada(
            @NotNull UUID id,
            @NotNull OffsetDateTime registradoEn,
            @Pattern(regexp = ORIGENES) String origen,
            @NotNull UUID loteId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") Double pesoPromedioG,
            @Min(1) Integer muestra) implements Entrada {

        public UUID padreId() { return loteId; }

        public boolean padreEsEstanque() { return false; }

        public Evento aEntidad() {
            Biometria b = new Biometria();
            b.setLoteId(loteId);
            b.setPesoPromedioG(pesoPromedioG);
            b.setMuestra(muestra);
            return b;
        }
    }
}
