package com.smartchargepark.demo.controller;

import com.smartchargepark.demo.dto.AvailabilityItem;
import com.smartchargepark.demo.model.Station;
import com.smartchargepark.demo.service.StationService;
import jakarta.validation.constraints.FutureOrPresent;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/stations")
public class StationController {
    private final StationService stationService;

    public StationController(StationService stationService) {
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

    @GetMapping("/{id}/availability")
    public List<AvailabilityItem> availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @FutureOrPresent LocalDate date) {
        return stationService.availability(id, date);
    }
}

