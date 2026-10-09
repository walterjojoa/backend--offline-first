package com.lacocha.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.FeedingRate;

public interface FeedingRateRepository extends JpaRepository<FeedingRate, Integer> {

    List<FeedingRate> findAllByOrderByPositionAsc();
}
