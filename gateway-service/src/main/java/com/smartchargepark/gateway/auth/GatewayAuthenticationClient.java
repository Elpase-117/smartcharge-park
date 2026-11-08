package com.smartchargepark.gateway.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class GatewayAuthenticationClient {
    private final WebClient client;
    private final Duration timeout;

    public GatewayAuthenticationClient(
            @Qualifier("authenticationWebClientBuilder") WebClient.Builder builder,
            @Value("${smartcharge.gateway.authentication.timeout-ms:2000}") long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Gateway authentication timeout must be positive");
        }
        client = builder.baseUrl("http://user-service").build();
        timeout = Duration.ofMillis(timeoutMs);
    }

    public Mono<UserIdentity> authenticate(String authorization) {
        return client.get().uri("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 401) {
                        return response.releaseBody().then(Mono.<UserIdentity>error(
                                new AuthenticationException(HttpStatus.UNAUTHORIZED)));
                    }
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().then(Mono.<UserIdentity>error(unavailable()));
                    }
                    return response.bodyToMono(UserIdentity.class)
                            .switchIfEmpty(Mono.error(unavailable()));
                })
                .flatMap(identity -> valid(identity) ? Mono.just(identity) : Mono.error(unavailable()))
                // Covers discovery, connection, response and body decoding; no blocking on event-loop threads.
                .timeout(timeout)
                .onErrorMap(error -> error instanceof AuthenticationException ? error : unavailable());
    }

    private boolean valid(UserIdentity identity) {
        return identity.userId() != null && identity.userId() > 0
                && identity.role() != null && identity.role().matches("[A-Z][A-Z0-9_]{0,31}");
    }

    private AuthenticationException unavailable() {
        return new AuthenticationException(HttpStatus.SERVICE_UNAVAILABLE);
    }

    public record UserIdentity(Long userId, String role) { }

    static class AuthenticationException extends RuntimeException {
        private final HttpStatus status;

        AuthenticationException(HttpStatus status) {
            super(status == HttpStatus.UNAUTHORIZED ? "identity-rejected" : "identity-provider-unavailable");
            this.status = status;
        }

        HttpStatus status() { return status; }
    }
}
