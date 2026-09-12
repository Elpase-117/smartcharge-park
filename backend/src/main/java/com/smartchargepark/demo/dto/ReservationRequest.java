package com.smartchargepark.demo.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ReservationRequest(
        @NotNull(message = "站点不能为空") Long stationId,
        @NotNull(message = "预约日期不能为空")
        @FutureOrPresent(message = "预约日期不能早于今天") LocalDate reservationDate,
        @NotBlank(message = "预约时段不能为空") String timeSlot
) {
}

