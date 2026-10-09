package com.lacocha.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LaCochaPropertiesTest {

    @Test
    void separaLosOrigenesPorComaYQuitaEspacios() {
        LaCochaProperties p = new LaCochaProperties(null, null, " https://panel.onrender.com , http://localhost:5173,,");
        assertThat(p.origins()).containsExactly("https://panel.onrender.com", "http://localhost:5173");
    }

    @Test
    void sinOrigenesDevuelveListaVacia() {
        assertThat(new LaCochaProperties(null, null, null).origins()).isEmpty();
    }
}
