package com.lacocha.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.WaterReading;

public interface WaterReadingRepository extends JpaRepository<WaterReading, UUID> {

    Optional<WaterReading> findFirstByPondIdAndTempCIsNotNullOrderByRecordedAtDesc(UUID pondId);
}
