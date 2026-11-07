package com.smartchargepark.reservation;

import com.smartchargepark.reservation.config.ReservationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.autoconfigure.ConfigurationPropertiesRebinderAutoConfiguration;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ConfigurationPropertiesRebinderAutoConfiguration.class))
            .withUserConfiguration(BusinessConfiguration.class);

    @Test
    void environmentChangesRebindSameBeanWithoutRefreshScope() {
        runner.withPropertyValues("reservation.max-days=7").run(context -> {
            ReservationProperties properties = context.getBean(ReservationProperties.class);
            assertThat(properties.getMaxDays()).isEqualTo(7);
            for (int days : new int[]{14, 7}) {
                TestPropertyValues.of("reservation.max-days=" + days).applyTo(context.getEnvironment());
                context.publishEvent(new EnvironmentChangeEvent(Set.of("reservation.max-days")));
                assertThat(context.getBean(ReservationProperties.class)).isSameAs(properties);
                assertThat(properties.getMaxDays()).isEqualTo(days);
            }
        });
    }

    @Test
    void negativeReservationWindowFailsValidation() {
        runner.withPropertyValues("reservation.max-days=-1").run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReservationProperties.class)
    static class BusinessConfiguration {
    }
}
