package com.lacocha.backend.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lacocha.backend.model.Alimentacion;

public interface AlimentacionRepository extends JpaRepository<Alimentacion, UUID> {

    @Query("select coalesce(sum(a.kg), 0.0) from Alimentacion a where a.loteId = :loteId and a.registradoEn >= :desde")
    double kgDelLoteDesde(@Param("loteId") UUID loteId, @Param("desde") Instant desde);

    @Query("select coalesce(sum(a.kg), 0.0) from Alimentacion a where a.loteId = :loteId")
    double kgTotalDelLote(@Param("loteId") UUID loteId);
}
