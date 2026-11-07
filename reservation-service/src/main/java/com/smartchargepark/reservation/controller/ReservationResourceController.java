package com.smartchargepark.reservation.controller;

import com.smartchargepark.reservation.dto.AvailabilityItem;
import com.smartchargepark.reservation.service.ReservationAvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationResourceController {
    private final ReservationAvailabilityService reservationService;

    public ReservationResourceController(ReservationAvailabilityService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/availability")
    public List<AvailabilityItem> availability(
            @RequestParam Long stationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reservationService.availability(stationId, date);
    }
}
