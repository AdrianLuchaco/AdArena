package com.adarena.earn.repository;

import com.adarena.earn.domain.SocialTaskReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SocialTaskReportRepository extends JpaRepository<SocialTaskReport, UUID> {

    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);

    List<SocialTaskReport> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
