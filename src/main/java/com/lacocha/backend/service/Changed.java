package com.lacocha.backend.service;

import java.util.Set;
import java.util.UUID;

/**
 * What a sync left behind, published once the transaction committed.
 *
 * Two listeners pick it up and they do very different things with it: {@link AiTrigger} analyses
 * what changed, and the event handler tells the browsers that something changed so their screen
 * refreshes on its own. Neither knows about the other, and the sync knows about neither.
 *
 * It is published after commit on purpose. Announcing a count that is still inside an open
 * transaction would mean announcing one that may yet roll back, and the screen would show a total
 * that the database never kept.
 *
 * Sets and not lists: a push of 500 counts from one batch is still one batch to look at.
 */
public record Changed(String deviceId, Set<UUID> ponds, Set<UUID> batches, int accepted) {

    public boolean isEmpty() {
        return ponds.isEmpty() && batches.isEmpty();
    }
}
