package com.smartchargepark.station;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.smartchargepark.station.mapper")
public class StationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StationServiceApplication.class, args);
    }
}
