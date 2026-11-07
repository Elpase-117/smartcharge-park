package com.smartchargepark.station.controller;

import com.smartchargepark.station.model.Station;
import com.smartchargepark.station.service.StationQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {
    private final StationQueryService stationService;

    public StationController(StationQueryService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public List<Station> list(@RequestParam(required = false) String keyword) {
        return stationService.list(keyword);
    }

    @GetMapping("/{id}")
    public Station detail(@PathVariable Long id) {
        return stationService.detail(id);
    }
}
