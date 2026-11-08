package com.smartchargepark.reservation;

import com.alibaba.cloud.sentinel.feign.SentinelFeignAutoConfiguration;
import com.alibaba.cloud.sentinel.feign.SentinelInvocationHandler;
import com.smartchargepark.reservation.client.OrderFeignClient;
import com.smartchargepark.reservation.client.StationFeignClient;
import com.smartchargepark.reservation.client.StationFeignFallbackFactory;
import com.smartchargepark.reservation.client.UserFeignClient;
import com.smartchargepark.reservation.dto.OrderCreateRequest;
import com.sun.net.httpserver.HttpServer;
import feign.Client;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class FeignTimeoutContractTest {
    private HttpServer provider;
    private ExecutorService executor;
    private final AtomicBoolean slow = new AtomicBoolean();
    private final CountDownLatch release = new CountDownLatch(1);
    private final OrderCreateRequest order = new OrderCreateRequest(99L, 2L, 4L, new BigDecimal("5.35"));

    @BeforeEach void startProvider() throws IOException {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        provider.setExecutor(executor);
        provider.createContext("/", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                if (slow.get()) {
                    try { release.await(8, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
                String path = exchange.getRequestURI().getPath();
                String json = path.startsWith("/internal/orders")
                        ? "{\"id\":10,\"orderNo\":\"SCP-test\",\"reservationId\":99,\"status\":\"PENDING_PAYMENT\",\"estimatedAmount\":5.35}"
                        : path.startsWith("/api/auth/me")
                        ? "{\"userId\":2,\"displayName\":\"测试用户\",\"role\":\"DRIVER\"}"
                        : "{\"id\":4,\"name\":\"测试站点\",\"slotCapacity\":2}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException expectedAfterTimeout) {
                // The read-timeout case closes its socket before this test-only provider replies.
            } finally { exchange.close(); }
        });
        provider.start();
    }

    @AfterEach void stopProvider() {
        release.countDown();
        provider.stop(0);
        executor.shutdownNow();
    }

    private ApplicationContextRunner context(int port) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class, FeignAutoConfiguration.class,
                        SentinelFeignAutoConfiguration.class))
                .withUserConfiguration(Clients.class)
                .withInitializer(application -> {
                    var yaml = new YamlPropertiesFactoryBean();
                    yaml.setResources(new ClassPathResource("application.yml"));
                    application.getEnvironment().getPropertySources().addLast(
                            new PropertiesPropertySource("production-yaml", yaml.getObject()));
                })
                .withPropertyValues("feign.sentinel.enabled=true",
                        "spring.cloud.openfeign.client.config.user-service.url=http://127.0.0.1:" + port,
                        "spring.cloud.openfeign.client.config.station-service.url=http://127.0.0.1:" + port,
                        "spring.cloud.openfeign.client.config.order-service.url=http://127.0.0.1:" + port);
    }

    private void options(RecordingClient transport) {
        assertThat(transport.options.get().connectTimeoutMillis()).isEqualTo(2000);
        assertThat(transport.options.get().readTimeoutMillis()).isEqualTo(3000);
    }

    @Test void allNamedClientsUseProductionTimeoutsAndKeepNormalDtos() {
        context(provider.getAddress().getPort()).run(application -> {
            assertThat(application).hasNotFailed();
            RecordingClient transport = application.getBean(RecordingClient.class);
            assertThat(application.getBean(UserFeignClient.class).current("Bearer unit-marker").userId()).isEqualTo(2L);
            options(transport);
            assertThat(application.getBean(StationFeignClient.class).getStation(4L).id()).isEqualTo(4L);
            options(transport);
            OrderFeignClient orders = application.getBean(OrderFeignClient.class);
            assertThat(Proxy.getInvocationHandler(orders)).isInstanceOf(SentinelInvocationHandler.class);
            assertThat(orders.create(order).reservationId()).isEqualTo(99L);
            options(transport);
            assertThat(transport.calls.get()).isEqualTo(3);
        });
    }

    @Test void refusedConnectionIsRecognizableAndWriteIsNotRetried() throws IOException {
        int unusedPort;
        try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            unusedPort = socket.getLocalPort();
        }
        context(unusedPort).run(application -> {
            var orders = application.getBean(OrderFeignClient.class);
            long start = System.nanoTime();
            Throwable failure = catchThrowable(() -> orders.create(order));
            assertThat(failure).isInstanceOf(RetryableException.class).hasRootCauseInstanceOf(ConnectException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));
            var transport = application.getBean(RecordingClient.class);
            options(transport);
            assertThat(transport.calls.get()).isEqualTo(1);
        });
    }

    @Test void productionReadTimeoutStopsWaitingAndRemainsRecognizable() {
        slow.set(true);
        context(provider.getAddress().getPort()).run(application -> {
            var orders = application.getBean(OrderFeignClient.class);
            long start = System.nanoTime();
            Throwable failure = catchThrowable(() -> orders.create(order));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertThat(failure).isInstanceOf(RetryableException.class).hasRootCauseInstanceOf(SocketTimeoutException.class);
            assertThat(((FeignException) failure).status()).isEqualTo(-1);
            assertThat(elapsed).isBetween(Duration.ofMillis(2500), Duration.ofSeconds(7));
            var transport = application.getBean(RecordingClient.class);
            options(transport);
            assertThat(transport.calls.get()).isEqualTo(1);
        });
    }

    static class RecordingClient implements Client {
        private final Client delegate = new Client.Default(null, null);
        final AtomicReference<Request.Options> options = new AtomicReference<>();
        final AtomicInteger calls = new AtomicInteger();
        @Override public Response execute(Request request, Request.Options current) throws IOException {
            options.set(current); calls.incrementAndGet();
            return delegate.execute(request, current);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableFeignClients(clients = {UserFeignClient.class, StationFeignClient.class, OrderFeignClient.class})
    @Import(StationFeignFallbackFactory.class)
    static class Clients {
        @Bean RecordingClient recordingClient() { return new RecordingClient(); }
    }
}
