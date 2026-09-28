package com.adarena.realtime;

import com.adarena.common.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Tiempo real con WebSocket + STOMP.
 * <ul>
 *   <li>Conexión: {@code ws(s)://<api>/ws} (solo desde los orígenes del frontend).</li>
 *   <li>{@code /topic/arena}: público. Portada + ranking cada vez que algo cambia.</li>
 *   <li>{@code /user/queue/notifications}: privado. Avisos como "te han superado".</li>
 * </ul>
 * El broker es "simple" (en memoria): suficiente con una sola instancia del backend.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final JwtDecoder jwtDecoder;

    public WebSocketConfig(AppProperties properties, JwtDecoder jwtDecoder) {
        this.properties = properties;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(properties.frontendOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthInterceptor(jwtDecoder));
    }
}
