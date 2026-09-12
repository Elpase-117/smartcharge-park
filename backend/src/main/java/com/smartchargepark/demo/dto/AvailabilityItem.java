package com.smartchargepark.demo.dto;

public record AvailabilityItem(String timeSlot, int capacity, long reserved, long remaining) {
}

