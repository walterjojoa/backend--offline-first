package com.lacocha.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.RangeParameter;

public interface RangeParameterRepository extends JpaRepository<RangeParameter, String> {

    List<RangeParameter> findAllByOrderByVariableAsc();
}
