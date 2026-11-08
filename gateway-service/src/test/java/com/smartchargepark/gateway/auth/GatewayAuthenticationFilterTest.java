package com.smartchargepark.gateway.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smartchargepark.gateway.filter.GatewayRequestLogFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static com.smartchargepark.gateway.auth.GatewayAuthenticationFilter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayAuthenticationFilterTest {
    private final GatewayAuthenticationClient authentication = mock(GatewayAuthenticationClient.class);
    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(authentication);

    @ParameterizedTest
    @ValueSource(strings = {"GET /api/stations", "GET /api/stations/4", "GET /api/reservations/availability",
            "POST /api/auth/login", "OPTIONS /api/reservations", "GET /actuator/health"})
    void publicAndOptionsRequestsDoNotRequireIdentityAndLoseSpoofedHeaders(String request) {
        String[] parts = request.split(" ");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(parts[0]), parts[1])
                .header(USER_ID_HEADER, "1").header(USER_ROLE_HEADER, "ADMIN"));
        AtomicInteger routed = new AtomicInteger();
        filter.filter(exchange, forwarded -> {
            routed.incrementAndGet();
            assertThat(forwarded.getRequest().getHeaders()).doesNotContainKeys(USER_ID_HEADER, USER_ROLE_HEADER);
            return Mono.empty();
        }).block(Duration.ofSeconds(3));
        assertThat(routed).hasValue(1);
        verifyNoInteractions(authentication);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /api/auth/me", "POST /api/reservations", "POST /api/stations",
            "GET /api/auth/login", "GET /api/stations/4/private"})
    void nonWhitelistedBusinessRequestsWithoutCredentialsNeverReachProvider(String request) {
        String[] parts = request.split(" ");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(parts[0]), parts[1]));
        assertRejected(exchange, 401);
        verifyNoInteractions(authentication);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Basic private-marker", "Bearer", "Bearer ", "Bearer two tokens", "Bearer token,other"})
    void malformedBearerIsRejectedLocally(String authorization) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authorization));
        assertRejected(exchange, 401);
        verifyNoInteractions(authentication);
    }

    @Test
    void duplicateAuthorizationHeadersAreRejected() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer first-marker", "Bearer second-marker"));
        assertRejected(exchange, 401);
        verifyNoInteractions(authentication);
    }

    @Test
    void validatedIdentityOverwritesEveryClientSuppliedIdentityValue() {
        when(authentication.authenticate("Bearer accepted-marker"))
                .thenReturn(Mono.just(new GatewayAuthenticationClient.UserIdentity(2L, "DRIVER")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/reservations")
                .header(HttpHeaders.AUTHORIZATION, "bearer accepted-marker")
                .header(USER_ID_HEADER, "1", "999").header(USER_ROLE_HEADER, "ADMIN"));
        AtomicInteger routed = new AtomicInteger();
        filter.filter(exchange, forwarded -> {
            routed.incrementAndGet();
            assertThat(forwarded.getRequest().getHeaders().get(USER_ID_HEADER)).containsExactly("2");
            assertThat(forwarded.getRequest().getHeaders().get(USER_ROLE_HEADER)).containsExactly("DRIVER");
            assertThat(forwarded.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer accepted-marker");
            return Mono.empty();
        }).block(Duration.ofSeconds(3));
        assertThat(routed).hasValue(1);
        verify(authentication, times(1)).authenticate("Bearer accepted-marker");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 503})
    void providerRejectionStopsRoutingWithItsAuthenticationStatus(int status) {
        when(authentication.authenticate(anyString())).thenReturn(Mono.error(
                new GatewayAuthenticationClient.AuthenticationException(HttpStatus.valueOf(status))));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer unit-marker"));
        assertRejected(exchange, status);
    }

    @Test
    void downstreamErrorsAreNotMisclassifiedAsAuthenticationFailures() {
        when(authentication.authenticate(anyString()))
                .thenReturn(Mono.just(new GatewayAuthenticationClient.UserIdentity(2L, "DRIVER")));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer unit-marker"));
        var failure = new ResponseStatusException(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.error(failure)).block(Duration.ofSeconds(3)))
                .isSameAs(failure);
    }

    @Test
    void surroundingAccessLoggerRecordsRejectedStatusWithoutCredentials() {
        var logger = (Logger) LoggerFactory.getLogger(GatewayRequestLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Basic private-marker")
                    .header(HttpHeaders.COOKIE, "session=private-cookie"));
            var access = new GatewayRequestLogFilter();
            assertThat(access.getOrder()).isLessThan(filter.getOrder());
            access.filter(exchange, forwarded -> filter.filter(forwarded,
                    ignored -> Mono.error(new AssertionError("Rejected request reached routing"))))
                    .block(Duration.ofSeconds(3));
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage())
                    .contains("path=/api/auth/me", "status=401")
                    .doesNotContain("private-", "Authorization", "Cookie");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertRejected(MockServerWebExchange exchange, int status) {
        AtomicInteger routed = new AtomicInteger();
        filter.filter(exchange, forwarded -> {
            routed.incrementAndGet();
            return Mono.empty();
        }).block(Duration.ofSeconds(3));
        assertThat(routed).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(3)))
                .contains("message").doesNotContain("marker", "Authorization", "Cookie", "exception");
    }
}
