package com.smartchargepark.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class GatewayRequestLogFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewayRequestLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            return Mono.defer(() -> chain.filter(exchange))
                    .doOnError(failure::set)
                    .doFinally(signal -> {
                        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                        log.info("[GatewayAccess] method={} path={} route={} status={} durationMs={}",
                                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                                route == null ? "NONE" : route.getId(),
                                responseStatus(exchange, failure.get(), signal),
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                    });
        });
    }

    private String responseStatus(ServerWebExchange exchange, Throwable failure, SignalType signal) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        if (status != null) {
            return Integer.toString(status.value());
        }
        if (failure instanceof ResponseStatusException exception) {
            return Integer.toString(exception.getStatusCode().value());
        }
        // Errors/cancellation may finish the filter before an outer error handler sets HTTP status.
        if (failure != null) {
            return "ERROR";
        }
        return signal == SignalType.CANCEL ? "CANCELLED" : "200";
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
