package com.adarena.site.service;

import com.adarena.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cola de lecturas de webs en segundo plano (2 a la vez). Pedir una lectura nunca hace esperar:
 * se apunta y vuelve al momento. Si esa web ya se está leyendo, no se vuelve a pedir.
 */
@Component
public class SitePreviewRefresher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SitePreviewRefresher.class);

    private final SitePreviewUpdater updater;
    private final boolean enabled;
    private final Duration defaultMaxAge;
    private final ThreadPoolTaskExecutor executor;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public SitePreviewRefresher(SitePreviewUpdater updater, AppProperties properties) {
        this.updater = updater;
        this.enabled = properties.previews().enabled();
        this.defaultMaxAge = properties.previews().maxAge();
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("adarena-sites-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.initialize();
    }

    /** Lee la web si hace falta (nunca leída, o leída hace más de 20 h). */
    public void request(String url, UUID ownerId) {
        request(url, ownerId, defaultMaxAge);
    }

    /** Igual, con una antigüedad máxima propia (p. ej. ZERO: "léela otra vez", respetando la espera mínima). */
    public void request(String url, UUID ownerId, Duration maxAge) {
        if (!enabled || url == null || ownerId == null || !inFlight.add(url)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    updater.refresh(url, ownerId, maxAge);
                } catch (RuntimeException e) {
                    log.warn("Reading {} failed", url, e);
                } finally {
                    inFlight.remove(url);
                }
            });
        } catch (TaskRejectedException e) {
            inFlight.remove(url);
            log.warn("Site preview queue is full; {} will be read later", url);
        }
    }

    /** ¿Se está leyendo ahora mismo? */
    public boolean isReading(String url) {
        return inFlight.contains(url);
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
