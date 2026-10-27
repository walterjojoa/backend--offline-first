package com.lacocha.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lacocha.backend.model.Finding;

public interface FindingRepository extends JpaRepository<Finding, UUID> {

    List<Finding> findTop100ByAttendedFalseOrderByCreatedAtDesc();

    List<Finding> findTop100ByPondIdOrderByCreatedAtDesc(UUID pondId);

    List<Finding> findTop100ByBatchIdOrderByCreatedAtDesc(UUID batchId);

    /**
     * What is already waiting to be looked at for this pond.
     *
     * This is what keeps the pond screen usable. The sensor measures every few seconds and a
     * falling oxygen trend is still the same trend on the next reading: without comparing against
     * what is already pending, one week of a slow drop would leave thousands of identical findings
     * and bury the one nobody has attended yet. Same idea the expert system already applies to its
     * own alerts in EventStore.saveNewAlerts.
     *
     * The whole list comes back rather than a boolean because the decision is not only "is there
     * one already": a trend that went from "in five days" to "tomorrow" has to be raised again.
     * That comparison needs the level of the one already there (see AiAnalysis.worthKeeping).
     */
    List<Finding> findByPondIdAndAttendedFalse(UUID pondId);

    List<Finding> findByBatchIdAndAttendedFalse(UUID batchId);

    long countByAttendedFalse();
}
