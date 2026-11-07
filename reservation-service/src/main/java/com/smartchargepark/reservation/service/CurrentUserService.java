package com.smartchargepark.reservation.service;

import com.smartchargepark.reservation.client.UserFeignClient;
import com.smartchargepark.reservation.dto.CurrentUser;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserService {
    private final UserFeignClient users;
    public CurrentUserService(UserFeignClient users) { this.users = users; }

    public Long requireUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        CurrentUser user;
        try {
            user = users.current(authorization);
        } catch (FeignException ex) {
            if (ex.status() == 401) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "用户服务暂不可用，请稍后重试");
        }
        if (user == null || user.userId() == null || user.userId() <= 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "用户服务未返回有效身份");
        }
        return user.userId();
    }
}
