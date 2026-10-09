package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lacocha.backend.model.Mortalidad;

public interface MortalidadRepository extends JpaRepository<Mortalidad, UUID> {

    @Query("select coalesce(sum(m.cantidad), 0) from Mortalidad m where m.loteId = :loteId")
    long totalDelLote(@Param("loteId") UUID loteId);

    @Query("select coalesce(sum(m.cantidad), 0) from Mortalidad m where m.loteId = :loteId and m.registradoEn > :desde")
    long totalDelLoteDespuesDe(@Param("loteId") UUID loteId, @Param("desde") Instant desde);
}
