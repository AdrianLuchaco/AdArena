package com.adarena.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.List;
import java.util.Set;

/**
 * Seguridad del WebSocket:
 * <ul>
 *   <li>CONNECT: si trae "Authorization: Bearer &lt;token&gt;", se valida el JWT y la conexión queda
 *       asociada a ese usuario (para recibir sus avisos privados). Sin token = visitante anónimo.</li>
 *   <li>SUBSCRIBE: solo a los dos canales permitidos. Nadie puede escuchar colas ajenas.</li>
 *   <li>SEND: prohibido. Los clientes solo escuchan; las acciones (pujar) van por la API REST.</li>
 * </ul>
 */
public class StompAuthInterceptor implements ChannelInterceptor {

    public static final String ARENA_TOPIC = "/topic/arena";
    public static final String NOTIFICATIONS_QUEUE = "/user/queue/notifications";
    private static final Set<String> ALLOWED_SUBSCRIPTIONS = Set.of(ARENA_TOPIC, NOTIFICATIONS_QUEUE);

    private final JwtDecoder jwtDecoder;

    public StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> {
                String destination = accessor.getDestination();
                if (destination == null || !ALLOWED_SUBSCRIPTIONS.contains(destination)) {
                    throw new MessageDeliveryException("Subscription not allowed: " + destination);
                }
                if (NOTIFICATIONS_QUEUE.equals(destination) && accessor.getUser() == null) {
                    throw new MessageDeliveryException("Login required for private notifications");
                }
            }
            case SEND -> throw new MessageDeliveryException("Clients cannot send messages");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, heartbeats...: permitidos
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || header.isBlank()) {
            return; // visitante anónimo: solo puede escuchar /topic/arena
        }
        if (!header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Invalid Authorization header");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            // El "nombre" del usuario en STOMP es su id: así convertAndSendToUser(id, …) le llega solo a él
            accessor.setUser(new UsernamePasswordAuthenticationToken(jwt.getSubject(), null, List.of()));
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }
}
