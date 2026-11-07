package com.smartchargepark.order.config;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationConfigurationTest {
    private ApplicationContextRunner runner(String database, AtomicBoolean sourceSeen) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class))
                .withUserConfiguration(DatabaseMigrationConfiguration.class)
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:" + database + ";DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
                        "spring.flyway.locations=classpath:db/migration-order-test")
                .withBean("dataSourceReadinessCheck", BeanPostProcessor.class, () -> new BeanPostProcessor() {
                    @Override
                    public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
                        if (bean instanceof DataSource source) {
                            // The same lifecycle stage where Seata wraps a DataSource must see migrated tables.
                            assertThat(new JdbcTemplate(source).queryForObject(
                                    "SELECT COUNT(*) FROM migration_order_probe", Integer.class)).isEqualTo(1);
                            sourceSeen.set(true);
                        }
                        return bean;
                    }
                });
    }

    @Test
    void migratesBeforeBusinessDataSourceIsPostProcessed() {
        AtomicBoolean sourceSeen = new AtomicBoolean();
        runner("migration_order_success", sourceSeen).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(sourceSeen).isTrue();
            assertThat(context).hasSingleBean(DataSource.class);
        });
    }

    @Test
    void migrationFailurePreventsBusinessDataSourceInitialization() {
        AtomicBoolean sourceSeen = new AtomicBoolean();
        runner("migration_order_failure", sourceSeen)
                .withBean(FlywayConfigurationCustomizer.class, () -> (FluentConfiguration config) ->
                        config.locations("classpath:db/migration-failure-test"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("migration_failure_probe");
                    assertThat(sourceSeen).isFalse();
                });
    }
}
