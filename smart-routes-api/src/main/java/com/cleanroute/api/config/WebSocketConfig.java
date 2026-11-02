package com.cleanroute.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.UUID;

/**
 * STOMP over WebSocket (with SockJS fallback) for live alerts.
 *
 * Clients connect to /ws and send "Authorization: Bearer <jwt>" in the STOMP CONNECT headers.
 * The user id becomes the session Principal, so the server sends alerts with
 * convertAndSendToUser(userId, "/queue/alerts", ...) and each client subscribes to
 * "/user/queue/alerts", receiving only its own alerts.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:4173}")
    private String[] allowedOrigins;

    public WebSocketConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String jwt = JwtAuthenticationFilter.resolveToken(accessor.getFirstNativeHeader("Authorization"));
                    UUID userId = jwt == null ? null : jwtAuthenticationFilter.authenticate(jwt).orElse(null);
                    if (userId == null) {
                        throw new MessageDeliveryException("Unauthorized WebSocket connection");
                    }
                    accessor.setUser(new UserPrincipal(userId.toString()));
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    // Only personal queues may be subscribed to; never another user's or a broadcast topic.
                    if (accessor.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                        throw new MessageDeliveryException("Subscription not allowed");
                    }
                }
                return message;
            }
        });
    }

    /** Minimal Principal whose name is the user's UUID. */
    record UserPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
