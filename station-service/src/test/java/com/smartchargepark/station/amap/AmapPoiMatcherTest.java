package com.smartchargepark.station.amap;

import com.smartchargepark.station.model.Station;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AmapPoiMatcherTest {
    private final AmapPoiMatcher matcher = new AmapPoiMatcher();
    private Station station() {
        var s = new Station(); s.setName("广汽能源汽车充电站（重庆北碚朝阳公园超充站）");
        s.setOperatorBrand("广汽能源"); s.setAddress("北碚区团山一路43号"); return s;
    }
    private AmapPoi poi(String id, String city, String district, String name, String address) {
        return new AmapPoi(id, name, address, new BigDecimal("106.429054"), new BigDecimal("29.831753"), city, district,
                "500109", "汽车服务;充电站;充电站");
    }
    private AmapPoi valid(String id) { return poi(id, "重庆市", "北碚区", "广汽能源汽车充电站(重庆北碚朝阳公园超充站)", "团山一路43号"); }
    @Test void acceptsExplicitBeibeiNameBrandAddressMatch() { assertThat(matcher.match(station(), List.of(valid("one")))).contains(valid("one")); }
    @Test void rejectsWrongCity() { assertThat(matcher.match(station(), List.of(poi("one", "成都市", "北碚区", station().getName(), "团山一路43号")))).isEmpty(); }
    @Test void rejectsTwoEquallyPlausiblePois() { assertThat(matcher.match(station(), List.of(valid("one"), valid("two")))).isEmpty(); }
    @Test void noCandidatesIsUnresolved() { assertThat(matcher.match(station(), List.of())).isEmpty(); }
    @Test void rejectsConflictingAddress() { assertThat(matcher.match(station(), List.of(poi("one", "重庆市", "北碚区", station().getName(), "完全不同的道路999号")))).isEmpty(); }
    @Test void rejectsDifferentBrand() { assertThat(matcher.match(station(), List.of(poi("one", "重庆市", "北碚区", "其他品牌充电站", "团山一路43号")))).isEmpty(); }
}
