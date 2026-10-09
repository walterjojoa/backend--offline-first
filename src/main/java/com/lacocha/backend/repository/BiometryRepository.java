package com.lacocha.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Biometry;

public interface BiometryRepository extends JpaRepository<Biometry, UUID> {

    Optional<Biometry> findFirstByBatchIdOrderByRecordedAtDesc(UUID batchId);
}
