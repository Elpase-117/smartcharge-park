package com.smartchargepark.reservation;

import com.smartchargepark.reservation.service.ReservationTimePolicy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

final class ReservationTestTime {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private ReservationTestTime() {
    }

    static ReservationTimePolicy at(String time) {
        var instant = TODAY.atTime(LocalTime.parse(time))
                .atZone(ReservationTimePolicy.BUSINESS_ZONE).toInstant();
        // Deliberately inject UTC; the policy must still use the Chongqing business zone.
        return new ReservationTimePolicy(Clock.fixed(instant, ZoneOffset.UTC));
    }
}
