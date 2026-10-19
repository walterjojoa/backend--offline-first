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

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private JsonNode send(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(req).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    private JsonNode withKey(MockHttpServletRequestBuilder req, int expectedStatus) throws Exception {
        return send(req.header("X-API-Key", API_KEY), expectedStatus);
    }

    private JsonNode postJson(String path, Map<String, Object> body, int expectedStatus) throws Exception {
        return withKey(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)), expectedStatus);
    }

    /** Closes a session the way the app does it: device key plus the token. */

    private void logout(String token, int expectedStatus) throws Exception {
        withKey(delete("/api/auth/sesion").header("Authorization", "Bearer " + token), expectedStatus);
    }
}

