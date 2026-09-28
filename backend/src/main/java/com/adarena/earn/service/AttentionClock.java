package com.adarena.earn.service;

import com.adarena.earn.repository.ProjectViewRepository;
import com.adarena.earn.repository.SocialTaskCompletionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * El "reloj de atención" de cada usuario: nadie puede mirar dos webs a la vez. Todo premio por
 * tiempo (un tramo de 10 s viendo un proyecto o una tarea de Créditos extra) se mide desde el
 * último premio del usuario, sea del tipo que sea. Así, abrir varias pestañas o varias tareas a
 * la vez no hace ganar más deprisa.
 */
@Component
public class AttentionClock {

    private final ProjectViewRepository viewRepository;
    private final SocialTaskCompletionRepository completionRepository;

    public AttentionClock(ProjectViewRepository viewRepository, SocialTaskCompletionRepository completionRepository) {
        this.viewRepository = viewRepository;
        this.completionRepository = completionRepository;
    }

    /** El último premio por tiempo del usuario (o null si nunca ha ganado nada así). */
    public Instant lastRewardAt(UUID userId) {
        return latest(viewRepository.findLastTickAt(userId).orElse(null),
                completionRepository.findLastCompletedAt(userId).orElse(null));
    }

    public static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
