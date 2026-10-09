package com.lacocha.backend.dto;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.model.Biometry;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.model.Feeding;
import com.lacocha.backend.model.FryCount;
import com.lacocha.backend.model.Mortality;
import com.lacocha.backend.model.WaterReading;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Events uploaded by the phone. The JSON "tipo" field decides which of these records is used. */
public final class Events {

    private Events() {
    }

    static final String SOURCES = "sensor|contador|voz|manual";

    public sealed interface Input permits WaterReadingInput, FryCountInput, MortalityInput,
            FeedingInput, BiometryInput {

        UUID id();

        OffsetDateTime recordedAt();

        String source();

        /** Pond (readings) or batch (all the others) the event belongs to. */
        UUID parentId();

        boolean parentIsPond();

        Event toEntity();

        /** Rules that do not fit in an annotation. Returns null when everything is fine. */
        default String extraError() {
            return null;
        }
    }

    /** JSON "tipo" -> record. LinkedHashMap so the error message lists them in order. */
    public static final Map<String, Class<? extends Input>> TYPES = new LinkedHashMap<>();

    static {
        TYPES.put("lectura_agua", WaterReadingInput.class);
        TYPES.put("conteo", FryCountInput.class);
        TYPES.put("mortalidad", MortalityInput.class);
        TYPES.put("alimentacion", FeedingInput.class);
        TYPES.put("biometria", BiometryInput.class);
    }

    public record WaterReadingInput(
            @NotNull UUID id,
            @JsonProperty("registrado_en") @NotNull OffsetDateTime recordedAt,
            @JsonProperty("origen") @Pattern(regexp = SOURCES) String source,
            @JsonProperty("estanque_id") @NotNull UUID pondId,
            @DecimalMin("-5") @DecimalMax("40") Double tempC,
            @DecimalMin("0") @DecimalMax("14") Double ph,
            @JsonProperty("oxigeno_mg_l") @DecimalMin("0") @DecimalMax("30") Double oxygenMgL,
            Double mv) implements Input {

        public UUID parentId() { return pondId; }

        public boolean parentIsPond() { return true; }

        @Override
        public String extraError() {
            return tempC == null && ph == null && oxygenMgL == null
                    ? "la lectura no trae temperatura, pH ni oxígeno"
                    : null;
        }

        public Event toEntity() {
            WaterReading r = new WaterReading();
            r.setPondId(pondId);
            r.setTempC(tempC);
            r.setPh(ph);
            r.setOxygenMgL(oxygenMgL);
            r.setMv(mv);
            return r;
        }
    }

    public record FryCountInput(
            @NotNull UUID id,
            @JsonProperty("registrado_en") @NotNull OffsetDateTime recordedAt,
            @JsonProperty("origen") @Pattern(regexp = SOURCES) String source,
            @JsonProperty("lote_id") @NotNull UUID batchId,
            @NotNull @Min(0) Integer total,
            @JsonProperty("cortes_multiples") @Min(0) Integer multipleCuts) implements Input {

        public UUID parentId() { return batchId; }

        public boolean parentIsPond() { return false; }

        public Event toEntity() {
            FryCount c = new FryCount();
            c.setBatchId(batchId);
            c.setTotal(total);
            c.setMultipleCuts(multipleCuts);
            return c;
        }
    }

    public record MortalityInput(
            @NotNull UUID id,
            @JsonProperty("registrado_en") @NotNull OffsetDateTime recordedAt,
            @JsonProperty("origen") @Pattern(regexp = SOURCES) String source,
            @JsonProperty("lote_id") @NotNull UUID batchId,
            @JsonProperty("cantidad") @NotNull @Min(1) Integer quantity,
            @JsonProperty("causa") @Size(max = 120) String cause) implements Input {

        public UUID parentId() { return batchId; }

        public boolean parentIsPond() { return false; }

        public Event toEntity() {
            Mortality m = new Mortality();
            m.setBatchId(batchId);
            m.setQuantity(quantity);
            m.setCause(cause);
            return m;
        }
    }

    public record FeedingInput(
            @NotNull UUID id,
            @JsonProperty("registrado_en") @NotNull OffsetDateTime recordedAt,
            @JsonProperty("origen") @Pattern(regexp = SOURCES) String source,
            @JsonProperty("lote_id") @NotNull UUID batchId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") Double kg) implements Input {

        public UUID parentId() { return batchId; }

        public boolean parentIsPond() { return false; }

        public Event toEntity() {
            Feeding f = new Feeding();
            f.setBatchId(batchId);
            f.setKg(kg);
            return f;
        }
    }

    public record BiometryInput(
            @NotNull UUID id,
            @JsonProperty("registrado_en") @NotNull OffsetDateTime recordedAt,
            @JsonProperty("origen") @Pattern(regexp = SOURCES) String source,
            @JsonProperty("lote_id") @NotNull UUID batchId,
            @JsonProperty("peso_promedio_g") @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("5000") Double avgWeightG,
            @JsonProperty("muestra") @Min(1) Integer sampleSize) implements Input {

        public UUID parentId() { return batchId; }

        public boolean parentIsPond() { return false; }

        public Event toEntity() {
            Biometry b = new Biometry();
            b.setBatchId(batchId);
            b.setAvgWeightG(avgWeightG);
            b.setSampleSize(sampleSize);
            return b;
        }
    }
}
