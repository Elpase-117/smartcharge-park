package com.smartchargepark.station.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class AmapPoiClient {
    private final AmapProperties properties;
    private final RestClient client;
    private final ObjectMapper json;

    @Autowired
    public AmapPoiClient(AmapProperties properties, ObjectMapper json) {
        this(properties, json, RestClient.builder().baseUrl("https://restapi.amap.com")
                .requestFactory(requestFactory()));
    }

    AmapPoiClient(AmapProperties properties, ObjectMapper json, RestClient.Builder builder) {
        this.properties = properties;
        this.json = json;
        this.client = builder.build();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    public List<AmapPoi> search(String keyword) {
        return request("/v3/place/text", "keywords", keyword);
    }

    public List<AmapPoi> detail(String id) {
        return request("/v3/place/detail", "id", id);
    }

    private List<AmapPoi> request(String path, String parameter, String value) {
        if (!properties.isConfigured()) throw new IllegalStateException("AMAP_WEB_KEY is not configured");
        try {
            String body = client.get().uri(builder -> builder.path(path)
                    .queryParam("key", properties.getWebKey()).queryParam(parameter, value)
                    .queryParam("city", "500109").queryParam("citylimit", "true")
                    .queryParam("offset", 25).queryParam("extensions", "all").build())
                    .retrieve().body(String.class);
            JsonNode root = json.readTree(body);
            if (root == null || !"1".equals(root.path("status").asText())) {
                throw new IllegalArgumentException("API rejected POI request");
            }
            if (!root.path("pois").isArray()) throw new IllegalArgumentException("Invalid POI response");
            List<AmapPoi> result = new ArrayList<>();
            for (JsonNode item : root.path("pois")) {
                String[] location = text(item, "location").split(",");
                if (location.length != 2) continue;
                BigDecimal lng = new BigDecimal(location[0]);
                BigDecimal lat = new BigDecimal(location[1]);
                if (lng.compareTo(BigDecimal.valueOf(73)) < 0 || lng.compareTo(BigDecimal.valueOf(136)) > 0
                        || lat.compareTo(BigDecimal.valueOf(3)) < 0 || lat.compareTo(BigDecimal.valueOf(54)) > 0) continue;
                result.add(new AmapPoi(text(item, "id"), text(item, "name"), text(item, "address"),
                        lng, lat, text(item, "cityname"), text(item, "adname"), text(item, "adcode"), text(item, "type")));
            }
            return result;
        } catch (Exception ignored) {
            // Never retain the HTTP exception: its message may include the key-bearing URL.
            throw new IllegalStateException("高德 POI 请求失败（网络、权限、配额或响应异常），未更新站点");
        }
    }

    private String text(JsonNode item, String field) {
        return item.path(field).isTextual() ? item.path(field).asText() : "";
    }
}
