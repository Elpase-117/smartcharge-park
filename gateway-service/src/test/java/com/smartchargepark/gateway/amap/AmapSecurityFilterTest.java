package com.smartchargepark.gateway.amap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.server.ServerWebExchange;
import static org.assertj.core.api.Assertions.assertThat;

class AmapSecurityFilterTest {
    @Test void statusIsLocalAndNeverExposesSecret() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/_AMapService/status"));
        new AmapSecurityFilter("proxy-fixture").filter(exchange, next -> { throw new AssertionError("must stay local"); }).block();
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("{\"configured\":true}");
    }
    @Test void addsServerSecretOnceAndDropsBusinessCredentials() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(org.springframework.http.HttpMethod.GET, java.net.URI.create("http://localhost/_AMapService/v3/direction/driving?key=fixture&jscode=untrusted&origin=106%2C29"))
                .header("Authorization", "Bearer fixture").header("Cookie", "fixture=value"));
        var forwarded = new AtomicReference<ServerWebExchange>();
        new AmapSecurityFilter("proxy-fixture").filter(exchange, next -> { forwarded.set(next); return Mono.empty(); }).block(Duration.ofSeconds(2));
        assertThat(forwarded.get().getRequest().getQueryParams().get("jscode")).containsExactly("proxy-fixture");
        assertThat(forwarded.get().getRequest().getQueryParams().getFirst("origin")).isEqualTo("106,29");
        assertThat(forwarded.get().getRequest().getHeaders()).doesNotContainKeys("Authorization", "Cookie");
    }
    @Test void noSecretReturns503AndDoesNotForward() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/_AMapService/v3/config/district"));
        new AmapSecurityFilter("").filter(exchange, next -> { throw new AssertionError("must not forward"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
    }
    @Test void preventsMutationAndUnversionedProxyTargets() {
        for (var request : new MockServerHttpRequest[]{MockServerHttpRequest.post("/_AMapService/v3/place/text").build(), MockServerHttpRequest.get("/_AMapService/other").build()}) {
            var exchange = MockServerWebExchange.from(request);
            new AmapSecurityFilter("fixture").filter(exchange, next -> { throw new AssertionError("must not forward"); }).block();
            assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(400);
        }
    }
    @Test void doesNotPropagateUrlBearingTransportException() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/_AMapService/v3/config/district"));
        new AmapSecurityFilter("fixture").filter(exchange, next -> Mono.error(new IllegalStateException("secret-bearing-URI"))).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(502);
    }
}
