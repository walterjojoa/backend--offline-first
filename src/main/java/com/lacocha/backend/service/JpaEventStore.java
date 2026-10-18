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

    public JpaEventStore(EntityManager em, AlertRepository alerts) {
        this.em = em;
        this.alerts = alerts;
    }

    @Override
    public boolean exists(Event event) {
        // Each kind of event has its own table, so the id is only looked up in the right one
        return em.find(event.getClass(), event.getId()) != null;
    }

    @Override
    public void save(Event event) {
        em.persist(event);
    }

    @Override
    public boolean parentExists(boolean parentIsPond, UUID parentId) {
        // In a variable and not inline: the ternary of two entity classes defeats type inference
        Class<?> parent = parentIsPond ? Pond.class : Batch.class;
        return em.find(parent, parentId) != null;
    }

    @Override
    public int saveNewAlerts(List<Alert> candidates) {
        int created = 0;
        for (Alert alert : candidates) {
            if (alerts.existsByPondIdAndVariableAndLevelAndAttendedFalse(
                    alert.getPondId(), alert.getVariable(), alert.getLevel())) {
                continue;
            }
            em.persist(alert);
            created++;
        }
        return created;
    }
}
