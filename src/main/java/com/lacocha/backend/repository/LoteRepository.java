package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Lote;

public interface LoteRepository extends JpaRepository<Lote, UUID> {

    List<Lote> findByServidorEnAfter(Instant desde);
}
