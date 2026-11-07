package com.smartchargepark.reservation.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record OrderCreateRequest(@NotNull @Positive Long reservationId, @NotNull @Positive Long userId,
        @NotNull @Positive Long stationId, @NotNull @DecimalMin("0.00") BigDecimal estimatedAmount) {}
