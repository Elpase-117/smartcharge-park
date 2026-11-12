package com.smartchargepark.gateway;

import org.junit.jupiter.api.Test;
import com.smartchargepark.gateway.filter.GatewayRequestLogFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "AMAP_JS_SECURITY_CODE=",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.nacos.discovery.health-indicator.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
class GatewayServiceApplicationTest {
    @Autowired
    private ApplicationContext context;

    @Autowired
    private WebTestClient client;

    @Autowired
    private RouteDefinitionLocator routes;

    @Autowired
    private GatewayProperties properties;

    @Test
    void missingMapSecretStillStartsAndProvidesCorsSafeLocalStatus() {
        client.get().uri("/_AMapService/status").header("Origin", "http://127.0.0.1:5173")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:5173")
                .expectBody().jsonPath("$.configured").isEqualTo(false);
    }

    @Test
    void startsReactiveServerAndExposesMinimalHealth() {
        assertThat(context).isInstanceOf(ReactiveWebServerApplicationContext.class);
        assertThat(context.getBeansOfType(GatewayRequestLogFilter.class)).hasSize(1);
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void bindsStationRouteToDiscoveredServiceWithoutPathChanges() {
        assertRoute("station-service-route", "lb://station-service", "/api/stations/**");
    }

    @Test
    void bindsReservationRouteToDiscoveredServiceWithoutPathChanges() {
        assertRoute("reservation-service-route", "lb://reservation-service", "/api/reservations/**");
    }

    @Test
    void bindsUserRouteToDiscoveredService() {
        assertRoute("user-service-route", "lb://user-service", "/api/auth/**");
    }

    @Test
    void answersBrowserPreflightCentrallyWithoutCallingProviders() {
        client.options().uri("/api/auth/login")
                .header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://127.0.0.1:5173");
    }

    @Test
    void rejectsUnconfiguredCorsOrigin() {
        client.options().uri("/api/reservations")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void bindsOneDefaultResponseHeaderFilter() {
        assertThat(properties.getDefaultFilters()).hasSize(1);
        assertThat(properties.getDefaultFilters().get(0).getName()).isEqualTo("AddResponseHeader");
        assertThat(properties.getDefaultFilters().get(0).getArgs())
                .containsValue("X-Gateway-Source").containsValue("SmartCharge-Park");
    }

    private void assertRoute(String id, String uri, String path) {
        RouteDefinition route = routes.getRouteDefinitions()
                .filter(candidate -> candidate.getId().equals(id))
                .single().block(Duration.ofSeconds(5));
        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo(uri);
        assertThat(route.getPredicates()).hasSize(1);
        assertThat(route.getPredicates().get(0).getName()).isEqualTo("Path");
        assertThat(route.getPredicates().get(0).getArgs()).containsValue(path);
        assertThat(route.getFilters()).isEmpty();
    }
}
