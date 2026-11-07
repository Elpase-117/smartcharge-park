package com.smartchargepark.order.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Arrays;
import java.util.LinkedHashSet;

/** Runs schema migration before Seata inspects the business data source's undo_log. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties({FlywayProperties.class, DataSourceProperties.class})
public class DatabaseMigrationConfiguration {
    @Bean
    Flyway flyway(DataSourceProperties owner, FlywayProperties properties,
                  ObjectProvider<FlywayConfigurationCustomizer> customizers) {
        // This raw connection is not a Spring DataSource bean, so Seata does not proxy DDL.
        // The URL and credentials come only from this service's business DataSource properties.
        DriverManagerDataSource migrationSource = new DriverManagerDataSource();
        migrationSource.setDriverClassName(owner.determineDriverClassName());
        migrationSource.setUrl(owner.determineUrl());
        migrationSource.setUsername(owner.determineUsername());
        migrationSource.setPassword(owner.determinePassword());
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(migrationSource)
                .locations(properties.getLocations().toArray(String[]::new))
                .encoding(properties.getEncoding())
                .table(properties.getTable())
                .baselineVersion(properties.getBaselineVersion())
                .baselineDescription(properties.getBaselineDescription())
                .baselineOnMigrate(properties.isBaselineOnMigrate())
                .cleanDisabled(properties.isCleanDisabled())
                .validateOnMigrate(properties.isValidateOnMigrate())
                .outOfOrder(properties.isOutOfOrder())
                .placeholders(properties.getPlaceholders());
        if (properties.getTarget() != null) {
            configuration.target(properties.getTarget());
        }
        customizers.orderedStream().forEach(customizer -> customizer.customize(configuration));
        return configuration.load();
    }

    @Bean
    FlywayMigrationInitializer flywayInitializer(Flyway flyway) {
        // Boot's initializer blocks startup and propagates any migration failure.
        return new FlywayMigrationInitializer(flyway);
    }

    @Bean
    static BeanFactoryPostProcessor migrateBeforeBusinessDataSource() {
        return beanFactory -> {
            // Retain Boot's Hikari configuration and Seata's automatic proxy creator unchanged.
            var definition = beanFactory.getBeanDefinition("dataSource");
            var dependencies = new LinkedHashSet<String>();
            if (definition.getDependsOn() != null) {
                dependencies.addAll(Arrays.asList(definition.getDependsOn()));
            }
            dependencies.add("flywayInitializer");
            definition.setDependsOn(dependencies.toArray(String[]::new));
        };
    }
}
