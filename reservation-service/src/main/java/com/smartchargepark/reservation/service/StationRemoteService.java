package com.smartchargepark.reservation.service;

import com.smartchargepark.reservation.client.StationFeignClient;
import com.smartchargepark.reservation.dto.StationSummary;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class StationRemoteService {
    private static final Logger log = LoggerFactory.getLogger(StationRemoteService.class);
    private final StationFeignClient stationFeignClient;

    public StationRemoteService(StationFeignClient stationFeignClient) {
        this.stationFeignClient = stationFeignClient;
    }

    public StationSummary detail(Long stationId) {
        log.info("Calling station-service through OpenFeign, stationId={}", stationId);
        StationSummary station;
        try {
            station = stationFeignClient.getStation(stationId);
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "站点不存在");
        } catch (FeignException ex) {
            log.warn("Feign station lookup failed for stationId={}: {}",
                    stationId, ex.getClass().getSimpleName());
            throw unavailable();
        }
        if (station == null || !Objects.equals(station.id(), stationId)
                || station.slotCapacity() == null || station.slotCapacity() < 0) {
            throw unavailable();
        }
        log.info("Feign station lookup succeeded, stationId={}, capacity={}", station.id(), station.slotCapacity());
        return station;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用，请稍后重试");
    }
}
