package com.smartchargepark.station.amap;

import com.smartchargepark.station.model.Station;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AmapPoiMatcher {
    public Optional<AmapPoi> match(Station station, List<AmapPoi> candidates) {
        var matches = candidates.stream().filter(poi -> eligible(station, poi)).distinct().toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private boolean eligible(Station station, AmapPoi poi) {
        if (!"重庆市".equals(poi.city()) || !"北碚区".equals(poi.district())
                || !"500109".equals(poi.adcode()) || !poi.type().contains("充电站")
                || poi.id().isBlank() || poi.longitude() == null || poi.latitude() == null) return false;
        String original = normalize(station.getName());
        String candidate = normalize(poi.name());
        boolean sameName = candidate.contains(original) || (candidate.length() >= 10 && original.contains(candidate));
        boolean sameBrand = List.of(station.getOperatorBrand().split("/")).stream()
                .map(AmapPoiMatcher::normalize).anyMatch(candidate::contains);
        String address = normalize(station.getAddress()).replace("重庆市", "").replace("北碚区", "");
        String poiAddress = normalize(poi.address()).replace("重庆市", "").replace("北碚区", "");
        boolean sameAddress = false;
        for (int i = 0; i + 5 <= address.length(); i++) {
            if (poiAddress.contains(address.substring(i, i + 5))) { sameAddress = true; break; }
        }
        return sameName && sameBrand && sameAddress;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]", "");
    }
}
