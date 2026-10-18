package com.lacocha.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Batch;
import com.lacocha.backend.model.Event;
import com.lacocha.backend.model.Pond;
import com.lacocha.backend.repository.AlertRepository;

import jakarta.persistence.EntityManager;

/**
 * The store the server really uses. It runs inside the push transaction on purpose: if the push
 * rolls back, nothing may look as if it had arrived, and no alert may be left pointing at a reading
 * nobody has.
 */
@Component
public class JpaEventStore implements EventStore {
    private final EntityManager em;

    private final AlertRepository alerts;
}
