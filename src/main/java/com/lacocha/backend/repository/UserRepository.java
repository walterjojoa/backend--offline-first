package com.lacocha.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lacocha.backend.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    /** The document is what the caretaker types to sign in. */
}

