package com.smartchargepark.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartchargepark.user.dto.*;
import com.smartchargepark.user.mapper.AppUserMapper;
import com.smartchargepark.user.model.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AuthService {
    private final AppUserMapper users;

    public AuthService(AppUserMapper users) { this.users = users; }

    public LoginResult login(LoginRequest request) {
        AppUser user = users.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, request.username()));
        if (user == null || !user.getPassword().equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        // Keep the existing Bearer contract; replace the old shared Demo constant.
        // One current opaque token per persisted user; a new login replaces the previous token.
        String token = UUID.randomUUID().toString();
        int updated = users.update(null, new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getId, user.getId()).set(AppUser::getAccessToken, token));
        if (updated != 1) { throw new IllegalStateException("登录身份未能保存"); }
        return new LoginResult(token, user.getId(), user.getDisplayName(), user.getRole());
    }

    public CurrentUser current(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        AppUser user = users.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getAccessToken, authorization.substring(7)));
        if (user == null) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"); }
        return new CurrentUser(user.getId(), user.getDisplayName(), user.getRole());
    }
}
