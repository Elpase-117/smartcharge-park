package com.smartchargepark.station.amap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "smartcharge.amap")
public class AmapProperties {
    private String webKey = "";
    public String getWebKey() { return webKey; }
    public void setWebKey(String webKey) { this.webKey = webKey; }
    public boolean isConfigured() { return webKey != null && !webKey.isBlank(); }
}
