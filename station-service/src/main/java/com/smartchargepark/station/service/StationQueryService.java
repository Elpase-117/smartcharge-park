package com.smartchargepark.station.service;

import com.smartchargepark.station.mapper.StationMapper;
import com.smartchargepark.station.model.Station;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.smartchargepark.common.web.BusinessException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StationQueryService {
    private final StationMapper stationMapper;

    public StationQueryService(StationMapper stationMapper) {
        this.stationMapper = stationMapper;
    }

    public List<Station> list(String keyword) {
        return stationMapper.findAll(StringUtils.hasText(keyword) ? keyword.trim() : null);
    }

    public Station detail(Long id) {
        Station station = stationMapper.findById(id);
        if (station == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "STATION_NOT_FOUND", "站点不存在");
        }
        return station;
    }
}
