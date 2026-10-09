package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Estanque;

public interface EstanqueRepository extends JpaRepository<Estanque, UUID> {

    List<Estanque> findAllByOrderByNombreAsc();

    List<Estanque> findByServidorEnAfter(Instant desde);
}
