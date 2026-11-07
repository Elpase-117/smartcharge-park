package com.smartchargepark.reservation;

import org.apache.seata.spring.boot.autoconfigure.properties.SeataProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeataConfigurationTest {
    private StandardEnvironment applicationEnvironment() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new PropertiesPropertySource("application", yaml.getObject()));
        return environment;
    }

    @Test
    void bindsActualSeataPropertiesForOneAutomaticAtProxy() {
        SeataProperties properties = Binder.get(applicationEnvironment())
                .bind("seata", Bindable.of(SeataProperties.class)).get();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getApplicationId()).isEqualTo("reservation-service");
        assertThat(properties.getTxServiceGroup()).isEqualTo("smartcharge_tx_group");
        assertThat(properties.isEnableAutoDataSourceProxy()).isTrue();
        assertThat(properties.getDataSourceProxyMode()).isEqualTo("AT");
    }

    @Test
    void keepsRegistryMappingAndOwnsReservationSchema() {
        StandardEnvironment environment = applicationEnvironment();
        Map<String, String> mapping = Binder.get(environment)
                .bind("seata.service.vgroup-mapping", Bindable.mapOf(String.class, String.class)).get();
        assertThat(mapping).containsEntry("smartcharge_tx_group", "default");
        assertThat(environment.getProperty("seata.registry.type")).isEqualTo("nacos");
        assertThat(environment.getProperty("seata.registry.nacos.application")).isEqualTo("seata-server");
        assertThat(environment.getProperty("seata.registry.nacos.group")).isEqualTo("SEATA_GROUP");
        assertThat(environment.getProperty("seata.registry.nacos.namespace")).isEmpty();
        assertThat(environment.getProperty("seata.config.type")).isEqualTo("file");
        assertThat(environment.getProperty("spring.datasource.hikari.read-only", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
    }
}
