package com.cleanroute.api;

import com.cleanroute.api.config.JwtTokenProvider;
import com.cleanroute.api.entity.Notification;
import com.cleanroute.api.entity.Route;
import com.cleanroute.api.entity.User;
import com.cleanroute.api.repository.RouteRepository;
import com.cleanroute.api.repository.UserRepository;
import com.cleanroute.api.service.NotificationService;
import com.cleanroute.api.util.SecurityHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that live alerts reach only their owner over a real STOMP/SockJS connection,
 * and that unauthenticated connections are refused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaHMyNTYtc2lnbmluZw==",
        "pollution.scheduler.enabled=false",
        "cors.allowed-origins=http://localhost"
})
@Testcontainers(disabledWithoutDocker = true)
class WebSocketAlertsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgis = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:15-3.4").asCompatibleSubstituteFor("postgres"));

    @LocalServerPort
    int port;

    @Autowired UserRepository userRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired NotificationService notificationService;

    private final WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));

    @AfterEach
    void stop() {
        stompClient.stop();
    }

    @Test
    void alertsReachOnlyTheirOwner() throws Exception {
        stompClient.setMessageConverter(anyTypeAsString());
        User alice = user();
        User bob = user();
        Route alicesRoute = route(alice);

        BlockingQueue<String> aliceInbox = new LinkedBlockingQueue<>();
        BlockingQueue<String> bobInbox = new LinkedBlockingQueue<>();
        StompSession aliceSession = connect(token(alice));
        StompSession bobSession = connect(token(bob));
        subscribe(aliceSession, aliceInbox);
        subscribe(bobSession, bobInbox);
        Thread.sleep(300); // let both subscriptions register

        notificationService.send(alicesRoute, Notification.Kind.POOR_AIR, "Poor air on test route", 90.0);

        String received = aliceInbox.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "owner should receive the alert");
        assertTrue(received.contains("Poor air on test route"));
        assertNull(bobInbox.poll(1, TimeUnit.SECONDS), "other users must not receive it");
    }

    @Test
    void connectWithoutToken_IsRefused() {
        stompClient.setMessageConverter(anyTypeAsString());
        assertThrows(Exception.class, () -> connect(null));
        assertThrows(Exception.class, () -> connect("not-a-valid-token"));
    }

    // ------------------------------------------------------------------ helpers

    /** Reads any payload (the server sends JSON) as a plain string. */
    private static StringMessageConverter anyTypeAsString() {
        return new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) {
                return true;
            }
        };
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost");
        CompletableFuture<StompSession> future = stompClient.connectAsync(
                "http://localhost:" + port + "/ws", handshakeHeaders, connectHeaders,
                new StompSessionHandlerAdapter() {});
        StompSession session = future.get(5, TimeUnit.SECONDS);
        // The server rejects a bad CONNECT by closing the session right after the handshake.
        Thread.sleep(300);
        if (!session.isConnected()) {
            throw new IllegalStateException("session closed by server");
        }
        return session;
    }

    private static void subscribe(StompSession session, BlockingQueue<String> inbox) {
        session.subscribe("/user/queue/alerts", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((String) payload);
            }
        });
    }

    private User user() {
        User user = new User();
        user.setName("WS user");
        user.setEmail("ws-" + UUID.randomUUID() + "@test.dev");
        user.setPasswordHash(SecurityHelper.hashPassword("irrelevant-pass"));
        user.setPasswordVersion(2);
        return userRepository.save(user);
    }

    private String token(User user) {
        return tokenProvider.generateToken(user.getId(), user.getEmail(), user.getTokenVersion());
    }

    private Route route(User owner) {
        Route route = new Route();
        route.setUser(owner);
        route.setName("Test route");
        route.setType("walking");
        route.setPath(new GeometryFactory(new PrecisionModel(), 4326).createLineString(new Coordinate[]{
                new Coordinate(77.2, 28.6), new Coordinate(77.3, 28.6)}));
        return routeRepository.save(route);
    }
}
