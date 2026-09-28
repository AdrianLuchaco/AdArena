package com.adarena.admin.service;

import com.adarena.admin.domain.AdminAuditLog;
import com.adarena.admin.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Rastro de cada acción de administración: quién, qué, cuándo y sobre qué. Se guarda en la MISMA
 * transacción que la acción: si la acción se deshace, el registro tampoco queda.
 */
@Service
public class AdminAuditService {

    private final AdminAuditLogRepository repository;
    private final JsonMapper jsonMapper;

    public AdminAuditService(AdminAuditLogRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void record(UUID adminId, String action, String targetType, Object targetId, Map<String, Object> details) {
        String json = details == null || details.isEmpty() ? null : jsonMapper.writeValueAsString(details);
        repository.save(new AdminAuditLog(adminId, action, targetType, String.valueOf(targetId), json));
    }
}
