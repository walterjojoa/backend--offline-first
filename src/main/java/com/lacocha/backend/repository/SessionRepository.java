package com.lacocha.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lacocha.backend.model.Session;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    /** Only the hash of the token is stored, so that is what a lookup goes by. */
}

