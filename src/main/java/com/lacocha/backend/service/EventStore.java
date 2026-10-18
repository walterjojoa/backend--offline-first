package com.lacocha.backend.service;

import java.util.List;
import java.util.UUID;

import com.lacocha.backend.model.Alert;
import com.lacocha.backend.model.Event;

/**
 * Implementor side of the Bridge: everything the ingestion of an event needs from storage, and
 * nothing else.
 *
 * The pipeline above it never sees an EntityManager. That is what lets the rules of what gets
 * accepted, duplicated or rejected be tested without a database, and what lets the storage change
 * without touching those rules.
 */
public interface EventStore {

    /** true when an event with this id is already stored: the phone retried after losing signal. */
    boolean exists(Event event);

    void save(Event event);

    /** The pond (water readings) or batch (everything else) the event points at. */
    boolean parentExists(boolean parentIsPond, UUID parentId);

    /**
     * Stores the alerts that are not already pending for the same pond, variable and level, and
     * returns how many were really created. The sensor measures every few seconds: repeating an
     * identical alert would bury the one the caretaker has not attended yet.
     */
    int saveNewAlerts(List<Alert> alerts);
}
