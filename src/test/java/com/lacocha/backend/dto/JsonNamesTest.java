package com.lacocha.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.dto.Catalog.BatchCreate;
import com.lacocha.backend.dto.Catalog.PondSync;
import com.lacocha.backend.dto.Events.WaterReadingInput;

class JsonNamesTest {

    /** A request with a list of records, like the ones the panel could send in bulk. */
    record Bulk(@JsonProperty("estanques") List<PondSync> ponds) {
    }

    @Test
    void usesTheJsonPropertyName() {
        assertThat(JsonNames.of(BatchCreate.class, "stockingDate")).isEqualTo("fecha_siembra");
    }

    @Test
    void followsListsAndKeepsTheIndex() {
        assertThat(JsonNames.of(Bulk.class, "ponds[2].updatedAt")).isEqualTo("estanques[2].actualizado_en");
    }

    @Test
    void withoutAnnotationFallsBackToSnakeCase() {
        assertThat(JsonNames.of(WaterReadingInput.class, "tempC")).isEqualTo("temp_c");
        assertThat(JsonNames.of(WaterReadingInput.class, "unknownField")).isEqualTo("unknown_field");
    }
}
