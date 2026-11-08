package com.smartchargepark.common.web;

/** Public error contract. Successful business DTOs are intentionally not wrapped. */
public record ErrorResponse(int status, String code, String message, String path) { }
