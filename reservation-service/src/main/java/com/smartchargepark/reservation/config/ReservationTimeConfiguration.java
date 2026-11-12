package com.smartchargepark.reservation.config;

import com.smartchargepark.reservation.service.ReservationTimePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ReservationTimeConfiguration {
    @Bean
    public Clock reservationClock() {
        return Clock.system(ReservationTimePolicy.BUSINESS_ZONE);
    }
}
