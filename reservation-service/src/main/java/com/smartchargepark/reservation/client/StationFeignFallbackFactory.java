package com.smartchargepark.reservation.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StationFeignFallbackFactory implements FallbackFactory<StationFeignClient> {
    private static final Logger log = LoggerFactory.getLogger(StationFeignFallbackFactory.class);

    @Override
    public StationFeignClient create(Throwable cause) {
        return stationId -> {
            if (cause instanceof FeignException.NotFound notFound) {
                // 保留 Provider 的 404，交给已有 StationRemoteService 转换业务语义。
                throw notFound;
            }
            log.warn("Sentinel fallback triggered for station-service, stationId={}, cause={}",
                    stationId, cause.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用，请稍后重试");
        };
    }
}
