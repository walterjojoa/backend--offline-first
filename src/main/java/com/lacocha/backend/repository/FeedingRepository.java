package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lacocha.backend.model.Feeding;

public interface FeedingRepository extends JpaRepository<Feeding, UUID> {

    @Query("select coalesce(sum(f.kg), 0.0) from Feeding f where f.batchId = :batchId and f.recordedAt >= :since")
    double kgForBatchSince(@Param("batchId") UUID batchId, @Param("since") Instant since);

    @Query("select coalesce(sum(f.kg), 0.0) from Feeding f where f.batchId = :batchId")
    double totalKgForBatch(@Param("batchId") UUID batchId);
}
