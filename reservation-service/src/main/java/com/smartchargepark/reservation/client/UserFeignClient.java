package com.smartchargepark.reservation.client;

import com.smartchargepark.reservation.dto.CurrentUser;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service")
public interface UserFeignClient {
    @GetMapping("/api/auth/me")
    CurrentUser current(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
