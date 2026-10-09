package com.lacocha.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.lacocha.backend.model.SyncLog;

public interface SyncLogRepository extends JpaRepository<SyncLog, UUID> {

    List<SyncLog> findTop200ByOrderByServerTimeDesc();

    List<SyncLog> findTop200ByDeviceIdOrderByServerTimeDesc(String deviceId);
}
