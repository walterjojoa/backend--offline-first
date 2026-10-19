package com.lacocha.backend.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacocha.backend.model.User;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What goes in and out of /api/auth. The JSON stays in Spanish, like the rest of the API. */
public final class Auth {
    private Auth() {
    }

    /** Digits only: it is an id number, and letting spaces or dots in makes it unmatchable later. */
}

