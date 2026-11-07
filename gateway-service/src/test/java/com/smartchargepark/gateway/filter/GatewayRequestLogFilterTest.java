package com.smartchargepark.gateway.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

class GatewayRequestLogFilterTest {
    private final GatewayRequestLogFilter filter = new GatewayRequestLogFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GatewayRequestLogFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    GatewayRequestLogFilterTest() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404})
    void logsMatchedRouteAndStatusWithoutChangingRequestOrResponse(int status) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/stations/4?token=private-query-marker")
                .header("Authorization", "Bearer private-header-marker")
                .header("Cookie", "session=private-cookie-marker"));
        Route route = Route.async().id("station-service-route").uri("lb://station-service")
                .predicate(ignored -> true).build();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        AtomicInteger calls = new AtomicInteger();
        GatewayFilterChain chain = forwarded -> {
            calls.incrementAndGet();
            assertThat(forwarded).isSameAs(exchange);
            forwarded.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
            return forwarded.getResponse().writeWith(Mono.just(forwarded.getResponse().bufferFactory()
                    .wrap("{\"message\":\"unchanged-body-marker\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(calls).hasValue(1);
        assertThat(exchange.getRequest().getMethod().name()).isEqualTo("GET");
        assertThat(exchange.getRequest().getURI().toString()).isEqualTo("/api/stations/4?token=private-query-marker");
        assertThat(exchange.getRequest().getHeaders().getFirst("Authorization")).isEqualTo("Bearer private-header-marker");
        assertThat(exchange.getRequest().getHeaders().getFirst("Cookie")).isEqualTo("session=private-cookie-marker");
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
        assertThat(exchange.getResponse().getBodyAsString().block(Duration.ofSeconds(5)))
                .isEqualTo("{\"message\":\"unchanged-body-marker\"}");
        assertThat(exchange.getResponse().getHeaders()).doesNotContainKey("X-Gateway-Source");
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("method=GET", "path=/api/stations/4", "route=station-service-route", "status=" + status, "durationMs=")
                .doesNotContain("private-", "unchanged-body-marker", "Authorization", "Cookie", "token=", "?");
    }

    @Test
    void handlesMissingRouteDefensively() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/defensive-test"));
        filter.filter(exchange, forwarded -> {
            forwarded.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return forwarded.getResponse().setComplete();
        }).block(Duration.ofSeconds(5));
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("route=NONE", "status=204");
        assertThat(filter.getOrder()).isEqualTo(-100);
    }

    @Test
    void preservesReactiveErrorAndLogsItsDeclaredStatus() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/error-test"));
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> filter.filter(exchange, ignored -> Mono.error(failure)).block(Duration.ofSeconds(5)))
                .isSameAs(failure);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("route=NONE", "status=503");
    }
}
