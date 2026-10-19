package com.lacocha.backend.controller;

/** Reads the token out of "Authorization: Bearer <token>". The only place that knows that shape. */
public final class Tokens {
    private static final String PREFIX = "Bearer ";

    private Tokens() {
    }
}

