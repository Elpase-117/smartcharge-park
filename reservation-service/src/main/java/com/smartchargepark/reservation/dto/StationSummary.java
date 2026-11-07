package com.smartchargepark.reservation.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
@JsonIgnoreProperties(ignoreUnknown = true)
public record StationSummary(Long id, String name, Integer slotCapacity, BigDecimal parkingFee, BigDecimal serviceFee) {
    public StationSummary(Long id, String name, Integer slotCapacity) { this(id, name, slotCapacity, null, null); }
}
