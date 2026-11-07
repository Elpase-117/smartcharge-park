package com.smartchargepark.reservation.service;

import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.dto.AvailabilityItem;
import com.smartchargepark.reservation.mapper.ReservationResourceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReservationAvailabilityService {
    private static final List<String> TIME_SLOTS = List.of(
            "09:00-10:00", "14:00-15:00", "19:00-20:00"
    );
    private final ReservationResourceMapper resourceMapper;
    private final StationRemoteService stationRemoteService;
    private final ReservationProperties properties;

    public ReservationAvailabilityService(ReservationResourceMapper resourceMapper,
                                          StationRemoteService stationRemoteService,
                                          ReservationProperties properties) {
        this.resourceMapper = resourceMapper;
        this.stationRemoteService = stationRemoteService;
        this.properties = properties;
    }

    public List<AvailabilityItem> availability(Long stationId, LocalDate date) {
        if (stationId == null || stationId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "站点编号必须为正整数");
        }
        LocalDate today = LocalDate.now();
        if (date == null || date.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预约日期不能早于当前日期");
        }
        int maxDays = properties.getMaxDays();
        if (date.isAfter(today.plusDays(maxDays))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "最多可提前预约未来 " + maxDays + " 天");
        }
        int capacity = stationRemoteService.detail(stationId).slotCapacity();
        return TIME_SLOTS.stream().map(slot -> {
            long reserved = resourceMapper.countActive(stationId, date, slot);
            return new AvailabilityItem(slot, capacity, reserved, Math.max(0L, capacity - reserved));
        }).toList();
    }
}
