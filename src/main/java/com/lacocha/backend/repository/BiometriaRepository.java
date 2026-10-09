package com.lacocha.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Biometria;

public interface BiometriaRepository extends JpaRepository<Biometria, UUID> {

    Optional<Biometria> findFirstByLoteIdOrderByRegistradoEnDesc(UUID loteId);
}
