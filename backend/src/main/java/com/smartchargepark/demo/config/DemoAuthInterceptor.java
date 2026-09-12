package com.smartchargepark.demo.config;

import com.smartchargepark.demo.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DemoAuthInterceptor implements HandlerInterceptor {
    public static final String DEMO_TOKEN = "smartcharge-demo-driver-token";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (!("Bearer " + DEMO_TOKEN).equals(authorization)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return true;
    }
}
