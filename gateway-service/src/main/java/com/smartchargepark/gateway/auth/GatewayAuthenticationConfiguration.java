package com.smartchargepark.gateway.auth;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class GatewayAuthenticationConfiguration {
    @Bean
    @LoadBalanced
    WebClient.Builder authenticationWebClientBuilder() {
        return WebClient.builder();
    }
}
