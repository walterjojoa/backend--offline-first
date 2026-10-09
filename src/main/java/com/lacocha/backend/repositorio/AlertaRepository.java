package com.lacocha.backend.repositorio;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.modelo.Alerta;

public interface AlertaRepository extends JpaRepository<Alerta, UUID> {

    List<Alerta> findTop100ByAtendidaFalseOrderByMedidoEnDesc();

    long countByEstanqueIdAndAtendidaFalse(UUID estanqueId);

    boolean existsByEstanqueIdAndVariableAndNivelAndAtendidaFalse(UUID estanqueId, String variable, String nivel);
}
