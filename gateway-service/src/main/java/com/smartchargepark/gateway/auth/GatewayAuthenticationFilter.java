package com.smartchargepark.gateway.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {
    public static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    public static final String USER_ROLE_HEADER = "X-Authenticated-User-Role";
    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);
    private static final PathPattern STATION_DETAIL = new PathPatternParser().parse("/api/stations/{id}");
    private static final Pattern BEARER = Pattern.compile("Bearer +([A-Za-z0-9._~+/-]+=*)", Pattern.CASE_INSENSITIVE);
    private final GatewayAuthenticationClient authentication;

    public GatewayAuthenticationFilter(GatewayAuthenticationClient authentication) {
        this.authentication = authentication;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove(USER_ID_HEADER);
            headers.remove(USER_ROLE_HEADER);
        })).build();
        String path = sanitized.getRequest().getPath().pathWithinApplication().value();
        if (HttpMethod.OPTIONS.equals(sanitized.getRequest().getMethod())
                || !path.startsWith("/api/") || isPublic(sanitized, path)) {
            return chain.filter(sanitized);
        }
        String authorization = bearer(sanitized.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION));
        if (authorization == null) {
            return reject(sanitized, HttpStatus.UNAUTHORIZED);
        }
        return authentication.authenticate(authorization)
                .map(identity -> {
                    ServerWebExchange authenticated = sanitized.mutate().request(request -> request.headers(headers -> {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                        headers.set(USER_ID_HEADER, identity.userId().toString());
                        headers.set(USER_ROLE_HEADER, identity.role());
                    })).build();
                    log.info("[GatewayAuth] outcome=authenticated userId={} role={} identityHeaders=replaced",
                            identity.userId(), identity.role());
                    return authenticated;
                })
                // Handle authentication errors before invoking the downstream chain; its errors retain their meaning.
                .onErrorResume(GatewayAuthenticationClient.AuthenticationException.class,
                        error -> reject(sanitized, error.status()).then(Mono.empty()))
                .flatMap(chain::filter);
    }

    private boolean isPublic(ServerWebExchange exchange, String path) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.POST.equals(method)) {
            return path.equals("/api/auth/login");
        }
        return HttpMethod.GET.equals(method) && (path.equals("/api/stations")
                || STATION_DETAIL.matches(exchange.getRequest().getPath().pathWithinApplication())
                || path.equals("/api/reservations/availability"));
    }

    private String bearer(List<String> values) {
        if (values == null || values.size() != 1) {
            return null;
        }
        var matcher = BEARER.matcher(values.get(0));
        return matcher.matches() ? "Bearer " + matcher.group(1) : null;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        log.info("[GatewayAuth] outcome=rejected status={}", status.value());
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        if (status == HttpStatus.UNAUTHORIZED) {
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        String body = status == HttpStatus.UNAUTHORIZED
                ? "{\"message\":\"请先登录或重新登录\"}" : "{\"message\":\"用户服务暂不可用，请稍后重试\"}";
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -90; // GatewayRequestLogFilter (-100) must also observe authentication failures.
    }
}
