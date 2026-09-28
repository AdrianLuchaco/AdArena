package com.adarena.earn.repository;

import com.adarena.earn.domain.SocialTaskCompletion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocialTaskCompletionRepository extends JpaRepository<SocialTaskCompletion, UUID> {

    List<SocialTaskCompletion> findByUserIdAndTaskDate(UUID userId, LocalDate taskDate);

    Optional<SocialTaskCompletion> findByTaskIdAndUserIdAndTaskDate(UUID taskId, UUID userId, LocalDate taskDate);

    /**
     * Crea la fila de hoy si no existe. "ON CONFLICT DO NOTHING": con un doble clic en "Visitar",
     * la segunda petición no inserta nada (en vez de fallar con un error de duplicado).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO social_task_completions (id, task_id, user_id, task_date, started_at)
            VALUES (gen_random_uuid(), :taskId, :userId, :taskDate, :now)
            ON CONFLICT (task_id, user_id, task_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("taskId") UUID taskId, @Param("userId") UUID userId,
                        @Param("taskDate") LocalDate taskDate, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM SocialTaskCompletion c
            WHERE c.taskId = :taskId AND c.userId = :userId AND c.taskDate = :taskDate
            """)
    Optional<SocialTaskCompletion> findForUpdate(@Param("taskId") UUID taskId, @Param("userId") UUID userId,
                                                 @Param("taskDate") LocalDate taskDate);

    long countByUserIdAndTaskDateAndCompletedAtIsNotNull(UUID userId, LocalDate taskDate);

    /** La última tarea que cobró este usuario (el "reloj de atención" lo comparten tareas y visitas). */
    @Query("SELECT max(c.completedAt) FROM SocialTaskCompletion c WHERE c.userId = :userId")
    Optional<Instant> findLastCompletedAt(@Param("userId") UUID userId);

    @Query("""
            SELECT COALESCE(SUM(c.pointsAwarded), 0) FROM SocialTaskCompletion c
            WHERE c.userId = :userId AND c.taskDate = :taskDate AND c.completedAt IS NOT NULL
            """)
    long sumPointsAwarded(@Param("userId") UUID userId, @Param("taskDate") LocalDate taskDate);

    /** Visitas de hoy a una tarea (para su dueño). */
    long countByTaskIdAndTaskDateAndCompletedAtIsNotNull(UUID taskId, LocalDate taskDate);
}
