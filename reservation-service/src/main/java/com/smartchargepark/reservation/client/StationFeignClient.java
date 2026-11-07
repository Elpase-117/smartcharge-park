package com.smartchargepark.reservation.client;

import com.smartchargepark.reservation.dto.StationSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "station-service", fallbackFactory = StationFeignFallbackFactory.class)
public interface StationFeignClient {
    @GetMapping("/api/stations/{id}")
    StationSummary getStation(@PathVariable("id") Long id);
}
