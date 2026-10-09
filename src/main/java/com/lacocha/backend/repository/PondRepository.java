package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Pond;

public interface PondRepository extends JpaRepository<Pond, UUID> {

    List<Pond> findAllByOrderByNameAsc();

    List<Pond> findByServerTimeAfter(Instant since);

    /** The name is unique in the database (uq_estanques_nombre). */
    boolean existsByNameAndIdNot(String name, UUID id);
}
