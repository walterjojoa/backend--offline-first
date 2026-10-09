package com.lacocha.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.lacocha.backend.dto.Catalog.BatchCreate;
import com.lacocha.backend.dto.Events.WaterReadingInput;
import com.lacocha.backend.dto.Sync.PushRequest;

class JsonNamesTest {

    @Test
    void usesTheJsonPropertyName() {
        assertThat(JsonNames.of(BatchCreate.class, "stockingDate")).isEqualTo("fecha_siembra");
    }

    @Test
    void followsListsAndKeepsTheIndex() {
        assertThat(JsonNames.of(PushRequest.class, "ponds[2].updatedAt")).isEqualTo("estanques[2].actualizado_en");
    }

    @Test
    void withoutAnnotationFallsBackToSnakeCase() {
        assertThat(JsonNames.of(WaterReadingInput.class, "tempC")).isEqualTo("temp_c");
        assertThat(JsonNames.of(WaterReadingInput.class, "unknownField")).isEqualTo("unknown_field");
    }
}
