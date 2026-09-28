package com.adarena.settings.service;

import com.adarena.admin.service.AdminAuditService;
import com.adarena.common.error.FieldValidationException;
import com.adarena.settings.domain.AppSettings;
import com.adarena.settings.dto.SettingsDto;
import com.adarena.settings.repository.AppSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Leer y cambiar la configuración de la Arena (solo admin; cada cambio queda auditado). */
@Service
public class SettingsService {

    private final AppSettingsRepository repository;
    private final AdminAuditService auditService;
    private final Clock clock;

    public SettingsService(AppSettingsRepository repository, AdminAuditService auditService, Clock clock) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettingsDto get() {
        return SettingsDto.from(repository.getSettings());
    }

    @Transactional
    public SettingsDto update(UUID adminId, SettingsDto request) {
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException e) {
            throw new FieldValidationException("timeZone", "That time zone does not exist (example: Europe/Madrid).");
        }
        AppSettings settings = repository.getSettings();
        SettingsDto before = SettingsDto.from(settings);
        settings.update(request.minBidPoints(), request.minIncrementPoints(), request.carryOverPercent(),
                LocalTime.parse(request.closeTime()), request.timeZone(), request.antiSnipingWindowSeconds(),
                request.antiSnipingExtensionSeconds(), request.antiSnipingMaxExtensions(), adminId, clock.instant());
        repository.flush();

        SettingsDto after = SettingsDto.from(settings);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", after);
        auditService.record(adminId, "SETTINGS_UPDATED", "APP_SETTINGS", AppSettings.SINGLETON_ID, details);
        return after;
    }
}
