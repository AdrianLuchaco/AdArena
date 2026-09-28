package com.adarena.user.repository;

import com.adarena.user.domain.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** El token BLOQUEADO: dos usos simultáneos del mismo enlace no pueden pasar los dos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PasswordResetToken t WHERE t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Cuántos enlaces se han pedido para un usuario desde {@code since} (anti-abuso). */
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant since);

    /** Al cambiar la contraseña, los demás enlaces pendientes dejan de servir. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAll(@Param("userId") UUID userId, @Param("now") Instant now);
}
