package com.smartchargepark.user.controller;

import com.smartchargepark.user.dto.*;
import com.smartchargepark.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }

    @GetMapping("/me")
    public CurrentUser me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return auth.current(authorization);
    }
}
