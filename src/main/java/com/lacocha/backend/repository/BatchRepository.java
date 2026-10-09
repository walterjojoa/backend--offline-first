package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Batch;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByServerTimeAfter(Instant since);

    /** The code is unique inside each pond (uq_lotes_estanque_codigo). */
    boolean existsByPondIdAndCodeAndIdNot(UUID pondId, String code, UUID id);
}
