package com.smartchargepark.demo.controller;

import com.smartchargepark.demo.dto.ReservationRequest;
import com.smartchargepark.demo.dto.ReservationResult;
import com.smartchargepark.demo.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ReservationResult create(@Valid @RequestBody ReservationRequest request) {
        return reservationService.create(request);
    }
}

