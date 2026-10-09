package com.lacocha.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Conteo;

public interface ConteoRepository extends JpaRepository<Conteo, UUID> {

    List<Conteo> findByLoteIdOrderByRegistradoEnAsc(UUID loteId);
}
