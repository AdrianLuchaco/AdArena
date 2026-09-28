package com.adarena.earn.repository;

import com.adarena.earn.domain.ProjectView;
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

public interface ProjectViewRepository extends JpaRepository<ProjectView, UUID> {

    /**
     * Crea la fila del día si no existe. "ON CONFLICT DO NOTHING": si dos pestañas la crean a la
     * vez, la segunda simplemente no inserta nada.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO project_views (id, viewer_id, owner_id, view_date, session_started_at)
            VALUES (gen_random_uuid(), :viewerId, :ownerId, :viewDate, :now)
            ON CONFLICT (viewer_id, owner_id, view_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfMissing(@Param("viewerId") UUID viewerId, @Param("ownerId") UUID ownerId,
                        @Param("viewDate") LocalDate viewDate, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProjectView v WHERE v.viewerId = :viewerId AND v.ownerId = :ownerId AND v.viewDate = :viewDate")
    Optional<ProjectView> findForUpdate(@Param("viewerId") UUID viewerId, @Param("ownerId") UUID ownerId,
                                        @Param("viewDate") LocalDate viewDate);

    /** El último tick de este usuario en CUALQUIER proyecto: nadie gana más rápido que el reloj. */
    @Query("SELECT max(v.lastTickAt) FROM ProjectView v WHERE v.viewerId = :viewerId")
    Optional<Instant> findLastTickAt(@Param("viewerId") UUID viewerId);

    List<ProjectView> findByViewerIdAndViewDate(UUID viewerId, LocalDate viewDate);

    @Query("SELECT COALESCE(SUM(v.pointsEarned), 0) FROM ProjectView v WHERE v.viewerId = :viewerId AND v.viewDate = :viewDate")
    long sumPointsEarned(@Param("viewerId") UUID viewerId, @Param("viewDate") LocalDate viewDate);

    /** Cuánta gente ha visto hoy un proyecto (para su dueño). */
    long countByOwnerIdAndViewDateAndTicksGreaterThan(UUID ownerId, LocalDate viewDate, int ticks);
}
