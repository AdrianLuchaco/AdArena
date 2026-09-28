package com.adarena.notification.repository;

import com.adarena.notification.domain.OutboxEmail;
import com.adarena.notification.domain.OutboxEmailStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEmailRepository extends JpaRepository<OutboxEmail, UUID> {

    /**
     * Deja un email en la bandeja de salida SALVO que ya exista uno con la misma clave
     * ({@code dedupKey}). "ON CONFLICT DO NOTHING" es clave: un duplicado no es un error y NO
     * debe deshacer la operación que lo provoca (por ejemplo, una puja).
     *
     * @return 1 si se ha guardado, 0 si ya existía
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO email_outbox (id, to_email, subject, html_body, text_body, status, attempts,
                                      next_attempt_at, dedup_key, created_at)
            VALUES (:id, :toEmail, :subject, :htmlBody, :textBody, 'PENDING', 0, :now, :dedupKey, :now)
            ON CONFLICT (dedup_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("toEmail") String toEmail, @Param("subject") String subject,
                       @Param("htmlBody") String htmlBody, @Param("textBody") String textBody,
                       @Param("dedupKey") String dedupKey, @Param("now") Instant now);

    /**
     * Siguiente tanda de emails por enviar, BLOQUEADOS. "SKIP LOCKED": si dos instancias del
     * backend lo ejecutan a la vez (p. ej. durante un despliegue), cada una coge emails distintos
     * y ninguno se envía dos veces.
     */
    @Query(value = """
            SELECT * FROM email_outbox
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEmail> lockDueBatch(@Param("now") Instant now, @Param("limit") int limit);

    long countByStatus(OutboxEmailStatus status);
}
