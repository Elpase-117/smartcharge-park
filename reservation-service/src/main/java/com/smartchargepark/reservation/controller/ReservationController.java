package com.smartchargepark.reservation.controller;
import com.smartchargepark.reservation.dto.*;
import com.smartchargepark.reservation.service.ReservationCreationService;
import com.smartchargepark.reservation.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {
    private final ReservationCreationService service;
    private final CurrentUserService users;
    public ReservationController(ReservationCreationService service, CurrentUserService users) {
        this.service = service; this.users = users;
    }
    @PostMapping
    public ReservationResult create(@Valid @RequestBody ReservationRequest request,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                    @RequestHeader(value = "X-Authenticated-User-Id", required = false) String gatewayUserId,
                                    @RequestHeader(value = "X-Authenticated-User-Role", required = false) String gatewayRole) {
        // Resolve the authenticated identity before opening the two-RM global transaction.
        Long userId = users.requireUserId(authorization, gatewayUserId, gatewayRole);
        return service.create(request, userId);
    }
}
