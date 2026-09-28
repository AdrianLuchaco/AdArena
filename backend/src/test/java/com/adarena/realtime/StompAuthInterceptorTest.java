package com.adarena.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reglas de seguridad del WebSocket, sin servidor (test unitario). */
class StompAuthInterceptorTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(decoder);
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> frame(StompCommand command, String destination, String authorization,
                                  boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        if (authenticated) accessor.setUser(new UsernamePasswordAuthenticationToken("user-1", null, List.of()));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void anonymousVisitorsCanConnectAndListenToTheArena() {
        interceptor.preSend(frame(StompCommand.CONNECT, null, null, false), channel);
        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/arena", null, false), channel);
    }

    @Test
    void aValidTokenIdentifiesTheUser() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("user-42")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(decoder.decode("token")).thenReturn(jwt);
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, "Bearer token", false);

        interceptor.preSend(connect, channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(connect);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("user-42");
    }

    @Test
    void anInvalidTokenIsRejected() {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("expired"));

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer bad", false), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void privateNotificationsRequireLogin() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/user/queue/notifications", null, false), channel))
                .isInstanceOf(MessageDeliveryException.class);

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/notifications", null, true), channel);
    }

    @Test
    void otherDestinationsAndSendingAreForbidden() {
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, "/queue/notifications-user-1", null, true), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/arena", null, true), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
