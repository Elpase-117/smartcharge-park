package com.smartchargepark.reservation.dto;

public record AvailabilityItem(String timeSlot, int capacity, long reserved, long remaining) {
}
