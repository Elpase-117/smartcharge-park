package com.smartchargepark.station.amap;

import com.smartchargepark.station.mapper.StationMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AmapStationSyncService {
    private final StationMapper stations;
    private final AmapPoiClient client;
    private final AmapPoiMatcher matcher;
    public AmapStationSyncService(StationMapper stations, AmapPoiClient client, AmapPoiMatcher matcher) {
        this.stations = stations; this.client = client; this.matcher = matcher;
    }

    public record Result(Long stationId, String originalName, String status, String reason,
                         AmapPoi poi, LocalDateTime syncedAt) {}

    public List<Result> sync() throws InterruptedException {
        List<Result> results = new ArrayList<>();
        for (var station : stations.findAll(null)) {
            if (station.getId() < 1 || station.getId() > 10) continue;
            try {
                var match = matcher.match(station, client.search(station.getName()));
                if (match.isPresent()) {
                    // Check detail again before persisting, not merely the first search hit.
                    Thread.sleep(1200);
                    match = matcher.match(station, client.detail(match.get().id()));
                }
                if (match.isEmpty()) {
                    results.add(new Result(station.getId(), station.getName(), "UNRESOLVED",
                            "候选不存在、存在歧义或名称/品牌/地址/行政区未同时通过校验", null, null));
                } else {
                    var timestamp = LocalDateTime.now().withNano(0);
                    if (stations.updatePoi(station.getId(), match.get(), timestamp) != 1) {
                        throw new IllegalStateException("Station was not updated");
                    }
                    results.add(new Result(station.getId(), station.getName(), "MATCHED", "名称、品牌、地址及行政区一致",
                            match.get(), timestamp));
                }
            } catch (IllegalStateException error) {
                results.add(new Result(station.getId(), station.getName(), "UNRESOLVED", "POI 请求或更新未完成", null, null));
            }
            // Maintenance only; avoid the provider's per-second request limit.
            Thread.sleep(1200);
        }
        return results;
    }
}
