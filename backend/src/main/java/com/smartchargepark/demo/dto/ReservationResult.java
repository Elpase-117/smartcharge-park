package com.smartchargepark.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReservationResult(
        Long reservationId,
        String reservationStatus,
        Long orderId,
        String orderNo,
        String orderStatus,
        BigDecimal estimatedAmount,
        Long stationId,
        LocalDate reservationDate,
        String timeSlot
) {
}

