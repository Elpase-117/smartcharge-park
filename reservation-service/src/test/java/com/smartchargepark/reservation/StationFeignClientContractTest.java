package com.smartchargepark.reservation;

import com.smartchargepark.reservation.client.StationFeignClient;
import com.smartchargepark.reservation.client.StationFeignFallbackFactory;
import com.smartchargepark.reservation.dto.StationSummary;
import com.alibaba.cloud.sentinel.feign.SentinelFeignAutoConfiguration;
import com.alibaba.cloud.sentinel.feign.SentinelInvocationHandler;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationFeignClientContractTest {
    private static HttpServer provider;
    private static final AtomicReference<String> lastRequest = new AtomicReference<>();

    @BeforeAll
    static void startHttpStub() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/api/stations/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            lastRequest.set(exchange.getRequestMethod() + " " + path);
            int status = path.equals("/api/stations/4") ? 200 : path.equals("/api/stations/5") ? 503 : 404;
            String json = status == 200
                    ? "{\"id\":4,\"name\":\"小鹏站\",\"slotCapacity\":2,\"powerSummary\":\"360 / 250 kW\"}"
                    : "{\"detail\":\"Provider error\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        provider.start();
    }

    @AfterAll
    static void stopHttpStub() {
        if (provider != null) {
            provider.stop(0);
        }
    }

    private ApplicationContextRunner feignContext() {
        // 契约测试使用随机端口 HTTP 桩；正式配置不设置 Provider URL。
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, FeignAutoConfiguration.class,
                        SentinelFeignAutoConfiguration.class))
                .withUserConfiguration(FeignClientsOnly.class)
                .withPropertyValues("feign.sentinel.enabled=true",
                        "spring.cloud.openfeign.client.config.station-service.url=http://127.0.0.1:"
                        + provider.getAddress().getPort());
    }

    @Test
    void springFeignProxyMapsJsonAndIgnoresExtraProviderFields() {
        feignContext().run(context -> {
            assertThat(context).hasNotFailed();
            StationFeignClient client = context.getBean(StationFeignClient.class);
            assertThat(Proxy.getInvocationHandler(client)).isInstanceOf(SentinelInvocationHandler.class);
            StationSummary station = client.getStation(4L);
            assertThat(station).isEqualTo(new StationSummary(4L, "小鹏站", 2));
            assertThat(lastRequest.get()).isEqualTo("GET /api/stations/4");
        });
    }

    @Test
    void springFeignProxyPreservesHttp404AsFeignNotFound() {
        feignContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(StationFeignClient.class).getStation(99999L))
                    .isInstanceOf(FeignException.NotFound.class);
            assertThat(lastRequest.get()).isEqualTo("GET /api/stations/99999");
        });
    }

    @Test
    void sentinelFeignProxyInvokesFallbackOnProviderFailure() {
        feignContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThatThrownBy(() -> context.getBean(StationFeignClient.class).getStation(5L))
                    .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(ex.getReason()).isEqualTo("站点服务暂不可用，请稍后重试");
                    });
            assertThat(lastRequest.get()).isEqualTo("GET /api/stations/5");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = StationFeignClient.class)
    @Import(StationFeignFallbackFactory.class)
    static class FeignClientsOnly {
    }
}
