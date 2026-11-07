package com.smartchargepark.user.dto;

public record LoginResult(String token, Long userId, String displayName, String role) {
}
