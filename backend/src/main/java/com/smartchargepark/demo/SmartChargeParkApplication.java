package com.smartchargepark.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.smartchargepark.demo.mapper")
public class SmartChargeParkApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartChargeParkApplication.class, args);
    }
}

