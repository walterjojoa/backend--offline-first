package com.lacocha.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.FryCount;

public interface FryCountRepository extends JpaRepository<FryCount, UUID> {

    List<FryCount> findByBatchIdOrderByRecordedAtAsc(UUID batchId);
}
