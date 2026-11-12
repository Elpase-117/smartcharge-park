package com.smartchargepark.gateway.amap;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.net.URI;

/** Fixed-host JS API proxy. No credentials or complete upstream URIs are logged. */
public final class AmapSecurityFilter implements GatewayFilter {
    private final String securityCode;
    public AmapSecurityFilter(String securityCode) { this.securityCode = securityCode; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.GET.equals(exchange.getRequest().getMethod())
                && "/_AMapService/status".equals(exchange.getRequest().getURI().getRawPath())) {
            exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().setCacheControl("no-store");
            byte[] body = ("{\"configured\":" + (securityCode != null && !securityCode.isBlank()) + "}")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }
        if (securityCode == null || securityCode.isBlank()) {
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return exchange.getResponse().setComplete();
        }
        String path = exchange.getRequest().getURI().getRawPath();
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())
                || !path.matches("/_AMapService/v[345]/[a-zA-Z0-9_./-]+")
                || path.contains("..")) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }
        var original = exchange.getRequest().getURI();
        String query = original.getRawQuery();
        // Discard client-supplied jscode, even if its name is percent-encoded.
        String clean = java.util.Arrays.stream((query == null ? "" : query).split("&"))
                .filter(part -> !part.isBlank()).filter(part -> !java.net.URLDecoder.decode(
                        part.split("=", 2)[0], java.nio.charset.StandardCharsets.UTF_8).equalsIgnoreCase("jscode"))
                .collect(java.util.stream.Collectors.joining("&"));
        String secret = java.net.URLEncoder.encode(securityCode, java.nio.charset.StandardCharsets.UTF_8);
        URI uri = URI.create(original.getScheme() + "://" + original.getRawAuthority() + path
                + "?" + (clean.isEmpty() ? "" : clean + "&") + "jscode=" + secret);
        var request = exchange.getRequest().mutate().uri(uri).headers(headers -> {
            headers.remove("Authorization"); headers.remove("Cookie");
            headers.remove("X-Authenticated-User-Id"); headers.remove("X-Authenticated-User-Role");
        }).build();
        return chain.filter(exchange.mutate().request(request).build())
                .onErrorResume(error -> {
                    // Do not propagate URI-bearing transport exceptions to error logs/clients.
                    if (!exchange.getResponse().isCommitted()) exchange.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
                    return exchange.getResponse().setComplete();
                });
    }
}
