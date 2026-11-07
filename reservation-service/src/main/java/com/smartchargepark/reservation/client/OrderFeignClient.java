package com.smartchargepark.reservation.client;
import com.smartchargepark.reservation.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
@FeignClient(name = "order-service")
public interface OrderFeignClient {
    @PostMapping("/internal/orders")
    OrderResult create(@RequestBody OrderCreateRequest request);
}
