package com.smartchargepark.order;
import com.smartchargepark.order.dto.*;
import com.smartchargepark.order.mapper.OrderMapper;
import com.smartchargepark.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:orders;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=false", "seata.enabled=false",
    "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:order-test-schema.sql",
    "spring.cloud.discovery.enabled=false", "spring.cloud.nacos.discovery.enabled=false",
    "spring.cloud.nacos.discovery.register-enabled=false", "spring.cloud.nacos.discovery.health-indicator.enabled=false",
    "spring.cloud.service-registry.auto-registration.enabled=false"
})
class OrderServiceTest {
    @Autowired OrderService service;
    @Autowired OrderMapper mapper;
    @Autowired TestRestTemplate http;
    @Test void startsContextAndExposesHealth() {
        assertThat(http.getForEntity("/actuator/health", String.class).getStatusCode().value()).isEqualTo(200);
    }
    @Test void insertsPendingOrderAndPreservesLegacyFields() {
        OrderResult result = service.create(new OrderCreateRequest(91L,1L,4L,new BigDecimal("5.35")));
        var row = mapper.selectById(result.id());
        assertThat(row.getReservationId()).isEqualTo(91L);
        assertThat(row.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(row.getEstimatedAmount()).isEqualByComparingTo("5.35");
        assertThat(row.getUserId()).isEqualTo(1L);
        assertThat(row.getOrderNo()).startsWith("SCP");
        assertThat(row.getCreatedAt()).isNotNull();
    }
    @Test void internalHttpCreateUsesRealMapper() {
        var response=http.postForEntity("/internal/orders",new OrderCreateRequest(92L,1L,4L,new BigDecimal("4.20")),OrderResult.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(mapper.selectById(response.getBody().id()).getReservationId()).isEqualTo(92L);
    }
}
