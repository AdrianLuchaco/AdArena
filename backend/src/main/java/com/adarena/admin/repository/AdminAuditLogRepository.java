package com.adarena.admin.repository;

import com.adarena.admin.domain.AdminAuditLog;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    List<AdminAuditLog> findAllByOrderByCreatedAtDesc(Limit limit);
}
