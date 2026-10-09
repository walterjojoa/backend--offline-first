package com.lacocha.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Alert;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findTop100ByAttendedFalseOrderByMeasuredAtDesc();

    long countByPondIdAndAttendedFalse(UUID pondId);

    boolean existsByPondIdAndVariableAndLevelAndAttendedFalse(UUID pondId, String variable, String level);
}
