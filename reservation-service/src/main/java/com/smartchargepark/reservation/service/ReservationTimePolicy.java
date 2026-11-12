package com.smartchargepark.reservation.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Component
public class ReservationTimePolicy {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> TIME_SLOTS = List.of(
            "09:00-10:00", "14:00-15:00", "19:00-20:00"
    );
    private final Clock clock;

    public ReservationTimePolicy(Clock clock) {
        this.clock = clock.withZone(BUSINESS_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public List<String> timeSlots() {
        return TIME_SLOTS;
    }

    public boolean isSupported(String timeSlot) {
        return timeSlot != null && TIME_SLOTS.contains(timeSlot);
    }

    public boolean isExpired(LocalDate date, String timeSlot) {
        return isExpired(date, timeSlot, now());
    }

    public boolean isExpired(LocalDate date, String timeSlot, LocalDateTime now) {
        if (!isSupported(timeSlot)) {
            throw new IllegalArgumentException("不支持的预约时段");
        }
        LocalTime start = LocalTime.parse(timeSlot.substring(0, 5));
        return !date.atTime(start).isAfter(now);
    }
}
