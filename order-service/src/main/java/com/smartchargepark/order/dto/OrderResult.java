package com.smartchargepark.order.dto;
import java.math.BigDecimal;
public record OrderResult(Long id, String orderNo, Long reservationId, String status, BigDecimal estimatedAmount) {}
