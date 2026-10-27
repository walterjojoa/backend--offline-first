package com.lacocha.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LaCochaPropertiesTest {

    @Test
    void splitsOriginsByCommaAndTrims() {
        LaCochaProperties p = new LaCochaProperties(null, null, " https://panel.onrender.com , http://localhost:5173,,", null, null, null);
        assertThat(p.origins()).containsExactly("https://panel.onrender.com", "http://localhost:5173");
    }

    @Test
    void noOriginsGivesEmptyList() {
        assertThat(new LaCochaProperties(null, null, null, null, null, null).origins()).isEmpty();
    }

    @Test
    void splitsProducerDocumentsTheSameWay() {
        LaCochaProperties p = new LaCochaProperties(null, null, null, " 1085123456 ,1098765432,,", null, null);
        assertThat(p.producers()).containsExactly("1085123456", "1098765432");
    }

    @Test
    void noProducersNamedGivesEmptyList() {
        // Vacio significa "es productor quien se registre primero", que es el arranque de una finca
        assertThat(new LaCochaProperties(null, null, null, null, null, null).producers()).isEmpty();
    }
}
