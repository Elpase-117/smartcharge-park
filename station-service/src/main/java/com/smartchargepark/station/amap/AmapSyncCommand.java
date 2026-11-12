package com.smartchargepark.station.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchargepark.station.StationServiceApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.nio.file.Path;

/** Explicit offline maintenance entry point; never invoked by normal service startup. */
public class AmapSyncCommand {
    public static void main(String[] args) throws Exception {
        try (var context = new SpringApplicationBuilder(StationServiceApplication.class)
                .web(WebApplicationType.NONE).properties(
                        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.discovery.enabled=false",
                        "spring.cloud.nacos.discovery.register-enabled=false").run(args)) {
            String report = context.getEnvironment().getRequiredProperty("smartcharge.amap.report");
            if (!context.getBean(AmapProperties.class).isConfigured()) {
                throw new IllegalStateException("AMAP_WEB_KEY is not configured; no station was changed");
            }
            var results = context.getBean(AmapStationSyncService.class).sync();
            context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter()
                    .writeValue(Path.of(report).toFile(), results);
            System.out.println("POI sync completed; matched=" + results.stream().filter(r -> r.status().equals("MATCHED")).count()
                    + ", unresolved=" + results.stream().filter(r -> r.status().equals("UNRESOLVED")).count());
        }
    }
}
