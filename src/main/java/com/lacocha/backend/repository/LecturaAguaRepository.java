package com.lacocha.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.LecturaAgua;

public interface LecturaAguaRepository extends JpaRepository<LecturaAgua, UUID> {

    Optional<LecturaAgua> findFirstByEstanqueIdAndTempCIsNotNullOrderByRegistradoEnDesc(UUID estanqueId);
}
