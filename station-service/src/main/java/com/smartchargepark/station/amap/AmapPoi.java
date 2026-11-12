package com.smartchargepark.station.amap;

import java.math.BigDecimal;

public record AmapPoi(String id, String name, String address, BigDecimal longitude,
                      BigDecimal latitude, String city, String district, String adcode, String type) {}
