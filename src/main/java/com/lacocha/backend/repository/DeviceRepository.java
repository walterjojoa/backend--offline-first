package com.lacocha.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.Device;

public interface DeviceRepository extends JpaRepository<Device, String> {

    List<Device> findAllByOrderByLastSeenAtDesc();
}
