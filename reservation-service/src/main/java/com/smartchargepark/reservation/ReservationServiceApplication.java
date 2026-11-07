package com.smartchargepark.reservation;

import com.smartchargepark.reservation.client.StationFeignClient;
import com.smartchargepark.reservation.client.OrderFeignClient;
import com.smartchargepark.reservation.client.UserFeignClient;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.experiment.RollbackExperimentProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableConfigurationProperties({ReservationProperties.class, RollbackExperimentProperties.class})
@EnableFeignClients(clients = {StationFeignClient.class, OrderFeignClient.class, UserFeignClient.class})
@MapperScan("com.smartchargepark.reservation.mapper")
public class ReservationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }
}
