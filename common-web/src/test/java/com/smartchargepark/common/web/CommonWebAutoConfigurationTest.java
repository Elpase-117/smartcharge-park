package com.smartchargepark.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommonWebAutoConfigurationTest {
    @Test
    void installsTheSharedAdviceInAServletApplication() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class))
                .run(context -> assertThat(context).hasSingleBean(CommonWebExceptionHandler.class));
    }

    @Test
    void doesNotInstallMvcAdviceInANonServletApplication() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(CommonWebExceptionHandler.class));
    }
}
