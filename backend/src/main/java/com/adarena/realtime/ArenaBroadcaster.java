package com.adarena.realtime;

import com.adarena.auction.event.ArenaChangedEvent;
import com.adarena.home.dto.HomeResponse;
import com.adarena.home.service.HomeService;
import com.adarena.notification.event.UserNotificationEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Difunde los cambios de la Arena por WebSocket.
 * <ul>
 *   <li>Solo DESPUÉS del commit (AFTER_COMMIT): nunca anunciamos una puja que luego se deshace.</li>
 *   <li>En SEGUNDO PLANO, en un hilo propio: la puja devuelve su conexión a la base de datos
 *       sin esperar al envío. (Si la difusión pidiera otra conexión mientras la puja aún tiene la
 *       suya, con muchas pujas a la vez se agotarían las conexiones y todo se bloquearía: lo
 *       detectó el test de pujas simultáneas.)</li>
 *   <li>AGRUPANDO: si llegan 40 pujas en un segundo, no se leen y envían 40 rankings. Se envía
 *       el estado más reciente en cuanto el hilo queda libre.</li>
 * </ul>
 * Los avisos privados ("te han superado") no necesitan la base de datos y salen al momento.
 */
@Component
public class ArenaBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ArenaBroadcaster.class);

    private final SimpMessagingTemplate messaging;
    private final HomeService homeService;
    private final TransactionTemplate readOnlyTransaction;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            runnable -> Thread.ofPlatform().name("arena-broadcaster").daemon().unstarted(runnable));
    private final AtomicBoolean refreshPending = new AtomicBoolean();

    public ArenaBroadcaster(SimpMessagingTemplate messaging, HomeService homeService,
                            PlatformTransactionManager transactionManager) {
        this.messaging = messaging;
        this.homeService = homeService;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArenaChanged(ArenaChangedEvent event) {
        for (UUID userId : event.outbidUserIds()) {
            messaging.convertAndSendToUser(userId.toString(), "/queue/notifications", ArenaNotification.outbid());
        }
        scheduleRankingBroadcast();
    }

    /** Avisos privados ("has ganado", "anuncio aprobado"…): al canal de ese usuario, tras el commit. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserNotification(UserNotificationEvent event) {
        messaging.convertAndSendToUser(event.userId().toString(), "/queue/notifications", event.notification());
    }

    private void scheduleRankingBroadcast() {
        if (!refreshPending.compareAndSet(false, true)) {
            return; // ya hay un envío en cola: incluirá también este cambio
        }
        worker.execute(() -> {
            // A partir de aquí, cualquier cambio nuevo programará otro envío
            refreshPending.set(false);
            try {
                HomeResponse home = readOnlyTransaction.execute(status -> homeService.getHome());
                messaging.convertAndSend(StompAuthInterceptor.ARENA_TOPIC, home);
            } catch (RuntimeException e) {
                // Un fallo al avisar nunca afecta a las pujas, que ya están guardadas
                log.warn("Could not broadcast the arena ranking", e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
