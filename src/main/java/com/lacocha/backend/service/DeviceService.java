package com.lacocha.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lacocha.backend.dto.Devices.DeviceResponse;
import com.lacocha.backend.dto.Devices.DeviceUpdate;
import com.lacocha.backend.dto.Devices.SyncLogResponse;
import com.lacocha.backend.model.Device;
import com.lacocha.backend.repository.DeviceRepository;
import com.lacocha.backend.repository.SyncLogRepository;

/** Devices are created by their first push; from here they only get a name or are deactivated. */
@Service
public class DeviceService {

    private final DeviceRepository devices;
    private final SyncLogRepository syncLog;

    public DeviceService(DeviceRepository devices, SyncLogRepository syncLog) {
        this.devices = devices;
        this.syncLog = syncLog;
    }

    /** The one that synced last comes first: a phone that stopped syncing is easy to spot. */
    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        return devices.findAllByOrderByLastSeenAtDesc().stream().map(DeviceResponse::from).toList();
    }

    /** Latest pushes, of one device or of all of them. */
    @Transactional(readOnly = true)
    public List<SyncLogResponse> syncLog(String deviceId) {
        return (deviceId == null
                ? syncLog.findTop200ByOrderByServerTimeDesc()
                : syncLog.findTop200ByDeviceIdOrderByServerTimeDesc(deviceId))
                .stream().map(SyncLogResponse::from).toList();
    }

    /** Never deleted: its events point to it. Deactivating (activo=false) stops its pushes. */
    @Transactional
    public DeviceResponse update(String id, DeviceUpdate data) {
        Device device = devices.findById(id).orElseThrow(() -> CatalogService.notFound("El dispositivo"));
        if (data.description() != null) device.setDescription(data.description());
        if (data.active() != null) device.setActive(data.active());
        devices.flush();
        return DeviceResponse.from(device);
    }
}
