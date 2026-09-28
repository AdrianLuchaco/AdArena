package com.adarena.earn.repository;

import com.adarena.earn.domain.SocialTask;
import com.adarena.earn.domain.SocialTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialTaskRepository extends JpaRepository<SocialTask, UUID> {

    /** Tareas que puede hacer un usuario: activas y de otros. Las menos visitadas primero (reparte el tráfico). */
    @Query("""
            SELECT t FROM SocialTask t
            WHERE t.status = com.adarena.earn.domain.SocialTaskStatus.ACTIVE AND t.ownerId <> :userId
            ORDER BY t.completions ASC, t.createdAt ASC
            """)
    List<SocialTask> findAvailableFor(@Param("userId") UUID userId, Limit limit);

    List<SocialTask> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerIdAndStatusIn(UUID ownerId, Collection<SocialTaskStatus> statuses);

    long countByStatus(SocialTaskStatus status);

    /** Las promociones publicadas más recientes (para tener sus webs leídas y al día). */
    List<SocialTask> findByStatusOrderByCreatedAtDesc(SocialTaskStatus status, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SocialTask t WHERE t.id = :id")
    Optional<SocialTask> findForUpdate(@Param("id") UUID id);

    /** Contadores con una suma directa en la base de datos: muchos usuarios a la vez nunca se pisan. */
    @Modifying
    @Query("UPDATE SocialTask t SET t.completions = t.completions + 1 WHERE t.id = :id")
    void incrementCompletions(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE SocialTask t SET t.reports = t.reports + 1 WHERE t.id = :id")
    void incrementReports(@Param("id") UUID id);

    /** Panel de administración: las más denunciadas y las ocultas primero. */
    @Query("""
            SELECT t FROM SocialTask t
            ORDER BY CASE WHEN t.status = com.adarena.earn.domain.SocialTaskStatus.HIDDEN THEN 0 ELSE 1 END,
                     t.reports DESC, t.createdAt DESC
            """)
    List<SocialTask> findForAdmin(Limit limit);

    @Query("SELECT count(t) FROM SocialTask t WHERE t.reports > 0 AND t.status <> com.adarena.earn.domain.SocialTaskStatus.HIDDEN")
    long countReportedVisible();
}
