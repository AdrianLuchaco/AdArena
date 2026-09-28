package com.adarena.realtime;

import com.adarena.TestcontainersConfiguration;
import com.adarena.adprofile.repository.AdProfileRepository;
import com.adarena.auction.repository.AuctionRepository;
import com.adarena.auction.service.BidService;
import com.adarena.image.service.ImageService;
import com.adarena.security.TokenService;
import com.adarena.support.ArenaFixtures;
import com.adarena.user.repository.UserRepository;
import com.adarena.wallet.service.WalletService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tiempo real de extremo a extremo: servidor de verdad en un puerto aleatorio, cliente STOMP
 * conectado por WebSocket y pujas reales. Comprueba que el ranking llega a todos y que el aviso
 * "te han superado" llega solo a quien corresponde.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ArenaWebSocketIntegrationTest {

    @LocalServerPort int port;
    @Autowired BidService bidService;
    @Autowired TokenService tokenService;
    @Autowired UserRepository userRepository;
    @Autowired AdProfileRepository adProfileRepository;
    @Autowired ImageService imageService;
    @Autowired WalletService walletService;
    @Autowired AuctionRepository auctionRepository;
    @Autowired JdbcTemplate jdbc;

    ArenaFixtures fixtures;
    WebSocketStompClient client;

    @BeforeEach
    void setUp() {
        fixtures = new ArenaFixtures(userRepository, adProfileRepository, imageService, walletService,
                auctionRepository, jdbc);
        fixtures.openRound(Duration.ofHours(1));
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    private StompSession connect(String bearerToken) throws Exception {
        WebSocketHttpHeaders http = new WebSocketHttpHeaders();
        http.setOrigin("http://localhost:3000"); // el origen del frontend, permitido
        StompHeaders stomp = new StompHeaders();
        if (bearerToken != null) stomp.add("Authorization", "Bearer " + bearerToken);
        return client.connectAsync("ws://localhost:" + port + "/ws", http, stomp, new StompSessionHandlerAdapter() {
        }).get(10, TimeUnit.SECONDS);
    }

    private static BlockingQueue<String> subscribe(StompSession session, String destination) throws InterruptedException {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class; // el servidor envía JSON
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add(String.valueOf(payload));
            }
        });
        Thread.sleep(300); // da tiempo a que el servidor registre la suscripción
        return messages;
    }

    @Test
    void everyVisitorReceivesTheNewRankingInstantly() throws Exception {
        BlockingQueue<String> arena = subscribe(connect(null), "/topic/arena");
        UUID bidder = fixtures.bidder("Proyecto Directo", 20_00);

        bidService.placeBid(bidder, 7_00, "ws-" + UUID.randomUUID());

        String message = arena.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull().contains("Proyecto Directo").contains("totalPoints=700");
    }

    @Test
    void onlyTheOutbidUserGetsThePrivateNotice() throws Exception {
        UUID ana = fixtures.bidder("Ana", 20_00);
        UUID luis = fixtures.bidder("Luis", 20_00);
        String anaToken = tokenService.issueAccessToken(userRepository.findById(ana).orElseThrow()).value();
        String luisToken = tokenService.issueAccessToken(userRepository.findById(luis).orElseThrow()).value();
        BlockingQueue<String> anaInbox = subscribe(connect(anaToken), "/user/queue/notifications");
        BlockingQueue<String> luisInbox = subscribe(connect(luisToken), "/user/queue/notifications");

        bidService.placeBid(ana, 5_00, "ws-" + UUID.randomUUID());
        bidService.placeBid(luis, 6_00, "ws-" + UUID.randomUUID());

        String notice = anaInbox.poll(5, TimeUnit.SECONDS);
        assertThat(notice).isNotNull().contains("type=OUTBID").contains("outbid you");
        assertThat(luisInbox.poll(1, TimeUnit.SECONDS)).isNull();
    }
}
