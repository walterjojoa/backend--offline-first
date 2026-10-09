package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lacocha.backend.model.Mortality;

public interface MortalityRepository extends JpaRepository<Mortality, UUID> {

    @Query("select coalesce(sum(m.quantity), 0) from Mortality m where m.batchId = :batchId")
    long totalForBatch(@Param("batchId") UUID batchId);

    @Query("select coalesce(sum(m.quantity), 0) from Mortality m where m.batchId = :batchId and m.recordedAt > :since")
    long totalForBatchAfter(@Param("batchId") UUID batchId, @Param("since") Instant since);
}
