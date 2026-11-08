package com.smartchargepark.gateway.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayAuthenticationClientTest {
    @Test
    void callsUserServiceDirectlyAndDecodesItsIdentity() {
        AtomicInteger calls = new AtomicInteger();
        var client = new GatewayAuthenticationClient(WebClient.builder().exchangeFunction(request -> {
            calls.incrementAndGet();
            assertThat(request.url().toString()).isEqualTo("http://user-service/api/auth/me");
            assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer unit-marker");
            assertThat(request.headers()).doesNotContainKey(GatewayAuthenticationFilter.USER_ID_HEADER);
            return Mono.just(response(HttpStatus.OK, "{\"userId\":2,\"displayName\":\"driver2\",\"role\":\"DRIVER\"}"));
        }), 2000);
        var identity = client.authenticate("Bearer unit-marker").block(Duration.ofSeconds(3));
        assertThat(identity).isEqualTo(new GatewayAuthenticationClient.UserIdentity(2L, "DRIVER"));
        assertThat(calls).hasValue(1);
    }

    @Test
    void invalidTokenRemainsUnauthorizedWithoutExposingUpstreamBody() {
        var client = responding(HttpStatus.UNAUTHORIZED, "upstream-private-marker");
        assertFailure(client, 401);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 500, 503})
    void unexpectedProviderStatusIsUnavailableRatherThanInvalidCredentials(int status) {
        assertFailure(responding(HttpStatus.valueOf(status), "upstream-private-marker"), 503);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "not-json", "{\"userId\":0,\"role\":\"DRIVER\"}",
            "{\"userId\":2}", "{\"userId\":2,\"role\":\"DRIVER\\r\\nInjected\"}"})
    void emptyOrMalformedIdentityFailsClosed(String body) {
        assertFailure(responding(HttpStatus.OK, body), 503);
    }

    @Test
    void connectionFailureIsUnavailable() {
        var client = new GatewayAuthenticationClient(WebClient.builder().exchangeFunction(request ->
                Mono.error(new IllegalStateException("transport-private-marker"))), 2000);
        assertFailure(client, 503);
    }

    @Test
    void neverCompletingProviderHasBoundedTimeout() {
        var client = new GatewayAuthenticationClient(WebClient.builder().exchangeFunction(request -> Mono.never()), 30);
        assertFailure(client, 503);
    }

    private GatewayAuthenticationClient responding(HttpStatus status, String body) {
        return new GatewayAuthenticationClient(WebClient.builder().exchangeFunction(request ->
                Mono.just(response(status, body))), 2000);
    }

    private ClientResponse response(HttpStatus status, String body) {
        return ClientResponse.create(status).header(HttpHeaders.CONTENT_TYPE, "application/json").body(body).build();
    }

    private void assertFailure(GatewayAuthenticationClient client, int status) {
        assertThatThrownBy(() -> client.authenticate("Bearer unit-marker").block(Duration.ofSeconds(3)))
                .isInstanceOfSatisfying(GatewayAuthenticationClient.AuthenticationException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(status);
                    assertThat(error.getMessage()).doesNotContain("private-marker", "unit-marker");
                    assertThat(error.getCause()).isNull();
                });
    }
}
