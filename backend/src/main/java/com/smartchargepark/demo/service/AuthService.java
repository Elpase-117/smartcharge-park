package com.smartchargepark.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartchargepark.demo.config.DemoAuthInterceptor;
import com.smartchargepark.demo.dto.LoginRequest;
import com.smartchargepark.demo.exception.BusinessException;
import com.smartchargepark.demo.mapper.AppUserMapper;
import com.smartchargepark.demo.model.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {
    private final AppUserMapper userMapper;

    public AuthService(AppUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public Map<String, Object> login(LoginRequest request) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, request.username()));
        if (user == null || !user.getPassword().equals(request.password())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", DemoAuthInterceptor.DEMO_TOKEN);
        result.put("userId", user.getId());
        result.put("displayName", user.getDisplayName());
        result.put("role", user.getRole());
        return result;
    }
}

