package com.smartchargepark.reservation.service;

import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.dto.AvailabilityItem;
import com.smartchargepark.reservation.mapper.ReservationResourceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationAvailabilityService {
    private final ReservationResourceMapper resourceMapper;
    private final StationRemoteService stationRemoteService;
    private final ReservationProperties properties;
    private final ReservationTimePolicy timePolicy;

    public ReservationAvailabilityService(ReservationResourceMapper resourceMapper,
                                          StationRemoteService stationRemoteService,
                                          ReservationProperties properties,
                                          ReservationTimePolicy timePolicy) {
        this.resourceMapper = resourceMapper;
        this.stationRemoteService = stationRemoteService;
        this.properties = properties;
        this.timePolicy = timePolicy;
    }

    public List<AvailabilityItem> availability(Long stationId, LocalDate date) {
        if (stationId == null || stationId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点编号必须为正整数");
        }
        LocalDateTime now = timePolicy.now();
        LocalDate today = now.toLocalDate();
        if (date == null || date.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预约日期不能早于当前日期");
        }
        int maxDays = properties.getMaxDays();
        if (date.isAfter(today.plusDays(maxDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多可提前预约未来 " + maxDays + " 天");
        }
        int capacity = stationRemoteService.detail(stationId).slotCapacity();
        return timePolicy.timeSlots().stream().map(slot -> {
            long reserved = resourceMapper.countActive(stationId, date, slot);
            return new AvailabilityItem(slot, capacity, reserved, Math.max(0L, capacity - reserved),
                    timePolicy.isExpired(date, slot, now));
        }).toList();
    }
}
