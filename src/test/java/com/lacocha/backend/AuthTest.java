package com.lacocha.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registering, signing in and what a session is good for, against H2 in memory.
 *
 * The point of these checks is the rule the whole thing rests on: either credential opens the API,
 * so adding people did not lock out the ESP32 and the phones that were already syncing with the
 * shared key.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthTest {
    private static final String API_KEY = "clave-prueba";

    /** Documents are unique in the database, so every test gets its own. */

    private static final AtomicLong NEXT_DOCUMENT = new AtomicLong(1_000_000_000L);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    // ---------- helpers ----------

    static String newDocument() {
        return String.valueOf(NEXT_DOCUMENT.incrementAndGet());
    }
}

