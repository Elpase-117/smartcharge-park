package com.smartchargepark.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartchargepark.demo.dto.AvailabilityItem;
import com.smartchargepark.demo.exception.BusinessException;
import com.smartchargepark.demo.mapper.ReservationMapper;
import com.smartchargepark.demo.mapper.StationMapper;
import com.smartchargepark.demo.model.Station;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
public class StationService {
    public static final List<String> TIME_SLOTS = List.of(
            "09:00-10:00", "14:00-15:00", "19:00-20:00"
    );

    private final StationMapper stationMapper;
    private final ReservationMapper reservationMapper;

    public StationService(StationMapper stationMapper, ReservationMapper reservationMapper) {
        this.stationMapper = stationMapper;
        this.reservationMapper = reservationMapper;
    }

    public List<Station> list(String keyword) {
        LambdaQueryWrapper<Station> query = new LambdaQueryWrapper<Station>().orderByAsc(Station::getId);
        if (StringUtils.hasText(keyword)) {
            query.and(q -> q.like(Station::getName, keyword).or().like(Station::getAddress, keyword));
        }
        return stationMapper.selectList(query);
    }

    public Station detail(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "站点不存在");
        }
        return station;
    }

    public List<AvailabilityItem> availability(Long stationId, LocalDate date) {
        Station station = detail(stationId);
        return TIME_SLOTS.stream().map(slot -> {
            long reserved = reservationMapper.countActive(stationId, date, slot);
            long remaining = Math.max(0, station.getSlotCapacity() - reserved);
            return new AvailabilityItem(slot, station.getSlotCapacity(), reserved, remaining);
        }).toList();
    }
}

