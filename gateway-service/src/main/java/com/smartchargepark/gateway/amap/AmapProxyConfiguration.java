package com.smartchargepark.gateway.amap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmapProxyConfiguration {
    @Bean
    RouteLocator amapRoutes(RouteLocatorBuilder builder,
                           @Value("${AMAP_JS_SECURITY_CODE:}") String securityCode) {
        var filter = new AmapSecurityFilter(securityCode);
        return builder.routes()
                .route("amap-styles", r -> r.path("/_AMapService/v4/map/styles")
                        .filters(f -> f.filter(filter).stripPrefix(1)).uri("https://webapi.amap.com"))
                .route("amap-service", r -> r.path("/_AMapService/**")
                        .filters(f -> f.filter(filter).stripPrefix(1)).uri("https://restapi.amap.com"))
                .build();
    }
}
