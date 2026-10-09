package com.lacocha.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.TemperatureFactor;

public interface TemperatureFactorRepository extends JpaRepository<TemperatureFactor, Integer> {

    List<TemperatureFactor> findAllByOrderByPositionAsc();
}
