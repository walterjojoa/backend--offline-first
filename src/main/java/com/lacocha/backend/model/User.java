package com.lacocha.backend.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A person who signs in. The device is the gadget and the user is the person: on the farm the
 * phones get lent around, so the two are kept apart and a session points at both.
 */
@Entity
@Table(name = "usuarios")
public class User {
    @Id
    private UUID id;

    /** The id number, which is what people on the farm know by heart. */

    @Column(name = "documento")
    private String document;

    @Column(name = "nombre")
    private String name;

    /** BCrypt. The plain password never reaches the database. */
}

