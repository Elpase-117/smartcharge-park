package com.smartchargepark.gateway.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Real Gateway HTTP/filter/LB chain, with an isolated HTTP provider instead of a database.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.nacos.discovery.health-indicator.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
class GatewayAuthenticationHttpTest {
    private static final HttpServer PROVIDER = provider();
    private static final AtomicInteger AUTHENTICATION_CALLS = new AtomicInteger();
    private static final AtomicInteger PROTECTED_CALLS = new AtomicInteger();
    private static final AtomicBoolean UNAVAILABLE = new AtomicBoolean();
    @Autowired private WebTestClient client;

    @DynamicPropertySource
    static void discoveredProviders(DynamicPropertyRegistry properties) {
        String uri = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
        for (String name : new String[]{"user-service", "station-service", "reservation-service"}) {
            properties.add("spring.cloud.discovery.client.simple.instances." + name + "[0].uri", () -> uri);
        }
    }

    @BeforeEach
    void reset() {
        AUTHENTICATION_CALLS.set(0);
        PROTECTED_CALLS.set(0);
        UNAVAILABLE.set(false);
    }

    @AfterAll
    static void stopProvider() { PROVIDER.stop(0); }

    @Test
    void loginAndPublicBrowseDoNotNeedBearerOrIdentityProvider() {
        UNAVAILABLE.set(true);
        client.post().uri("/api/auth/login").bodyValue("{}").exchange().expectStatus().isOk();
        client.get().uri("/api/stations").exchange().expectStatus().isOk();
        client.get().uri("/api/stations/4").exchange().expectStatus().isOk();
        client.get().uri("/api/reservations/availability?stationId=4&date=2030-01-01")
                .exchange().expectStatus().isOk();
        assertThat(AUTHENTICATION_CALLS).hasValue(0);
    }

    @Test
    void missingAndMalformedAuthorizationAreRejectedBeforeAnyProviderCall() {
        client.get().uri("/api/auth/me").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/reservations").bodyValue("{}").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/auth/me").header("Authorization", "Basic malformed-marker")
                .exchange().expectStatus().isUnauthorized();
        assertThat(AUTHENTICATION_CALLS).hasValue(0);
        assertThat(PROTECTED_CALLS).hasValue(0);
    }

    @Test
    void invalidTokenIsCheckedOnceAndNotForwardedToBusiness() {
        client.post().uri("/api/reservations").header("Authorization", "Bearer rejected-marker")
                .bodyValue("{}").exchange().expectStatus().isUnauthorized();
        assertThat(AUTHENTICATION_CALLS).hasValue(1);
        assertThat(PROTECTED_CALLS).hasValue(0);
    }

    @Test
    void validTokenOverridesSpoofedHeadersOnTheActualForwardedHttpRequest() {
        client.post().uri("/api/reservations").header("Authorization", "Bearer accepted-marker")
                .header("X-Authenticated-User-Id", "1", "999")
                .header("X-Authenticated-User-Role", "ADMIN")
                .bodyValue("{\"userId\":1}").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("X-Gateway-Source", "SmartCharge-Park")
                .expectBody().jsonPath("$.receivedUserId").isEqualTo("2")
                .jsonPath("$.receivedRole").isEqualTo("DRIVER");
        assertThat(AUTHENTICATION_CALLS).hasValue(1);
        assertThat(PROTECTED_CALLS).hasValue(1);
    }

    @Test
    void providerOutageIs503AndCorsPreflightStillWorks() {
        UNAVAILABLE.set(true);
        client.get().uri("/api/auth/me").header("Authorization", "Bearer accepted-marker")
                .exchange().expectStatus().isEqualTo(503);
        client.options().uri("/api/reservations")
                .header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:5173");
        assertThat(AUTHENTICATION_CALLS).hasValue(1);
        assertThat(PROTECTED_CALLS).hasValue(0);
    }

    private static HttpServer provider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", GatewayAuthenticationHttpTest::respond);
            server.start();
            return server;
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static void respond(HttpExchange request) throws IOException {
        request.getRequestBody().readAllBytes();
        String path = request.getRequestURI().getPath();
        String id = request.getRequestHeaders().getFirst("X-Authenticated-User-Id");
        String role = request.getRequestHeaders().getFirst("X-Authenticated-User-Role");
        int status = 200;
        String body = "{}";
        if (path.equals("/api/auth/me") && id == null) {
            AUTHENTICATION_CALLS.incrementAndGet();
            if (UNAVAILABLE.get()) {
                status = 503;
            } else if (!"Bearer accepted-marker".equals(request.getRequestHeaders().getFirst("Authorization"))) {
                status = 401;
            } else {
                body = "{\"userId\":2,\"displayName\":\"driver2\",\"role\":\"DRIVER\"}";
            }
        } else if (path.equals("/api/reservations")) {
            PROTECTED_CALLS.incrementAndGet();
            body = "{\"receivedUserId\":\"" + id + "\",\"receivedRole\":\"" + role + "\"}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(status, bytes.length);
        request.getResponseBody().write(bytes);
        request.close();
    }
}
