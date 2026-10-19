package com.lacocha.backend.controller;

/** Reads the token out of "Authorization: Bearer <token>". The only place that knows that shape. */
public final class Tokens {

    private static final String PREFIX = "Bearer ";

    private Tokens() {
    }

    /** null when the header is missing or is not a bearer: the caller decides what that means. */
    public static String of(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIX)) {
            return null;
        }
        String token = authorization.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
