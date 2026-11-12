package com.smartchargepark.station.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AmapPoiClientTest {
    private MockRestServiceServer server;
    private AmapPoiClient client;
    private AmapProperties properties;
    private static final String POI = """
            {"id":"fixture-poi","name":"充电站","address":"团山一路43号","location":"106.429054,29.831753",
             "cityname":"重庆市","adname":"北碚区","adcode":"500109","type":"汽车服务;充电站;充电站"}
            """;
    @BeforeEach void setup() {
        properties = new AmapProperties(); properties.setWebKey("unit-test-placeholder");
        var builder = RestClient.builder().baseUrl("https://restapi.amap.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AmapPoiClient(properties, new ObjectMapper(), builder);
    }
    private void response(String body) {
        server.expect(queryParam("key", "unit-test-placeholder"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
    @Test void parsesSuccessfulSearchWithConstrainedRegion() {
        server.expect(queryParam("city", "500109")).andExpect(queryParam("citylimit", "true"))
                .andRespond(withSuccess("{\"status\":\"1\",\"pois\":[" + POI + "]}", MediaType.APPLICATION_JSON));
        var result = client.search("充电站");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).longitude()).isEqualByComparingTo("106.429054");
        assertThat(result.get(0).adcode()).isEqualTo("500109"); server.verify();
    }
    @Test void emptySearchReturnsNoInventedPoi() {
        response("{\"status\":\"1\",\"pois\":[]}"); assertThat(client.search("站点")).isEmpty();
    }
    @Test void preservesMultipleCandidatesForMatcher() {
        response("{\"status\":\"1\",\"pois\":[" + POI + "," + POI + "]}");
        assertThat(client.search("站点")).hasSize(2);
    }
    @Test void rejectsApiErrorWithoutLeakingProviderBodyOrKey() {
        response("{\"status\":\"0\",\"info\":\"unit-test-placeholder\"}");
        assertThatThrownBy(() -> client.search("站点")).isInstanceOf(IllegalStateException.class)
                .hasNoCause().hasMessageNotContaining("unit-test-placeholder");
    }
    @Test void rejectsInvalidJson() {
        response("not json"); assertThatThrownBy(() -> client.search("站点")).isInstanceOf(IllegalStateException.class).hasNoCause();
    }
    @Test void missingKeyDoesNotCallNetwork() {
        properties.setWebKey(""); assertThatThrownBy(() -> client.search("站点")).hasMessageContaining("not configured"); server.verify();
    }
    @Test void detailUsesPoiId() {
        server.expect(queryParam("id", "fixture-poi")).andRespond(withSuccess("{\"status\":\"1\",\"pois\":[" + POI + "]}", MediaType.APPLICATION_JSON));
        assertThat(client.detail("fixture-poi")).hasSize(1); server.verify();
    }
    @Test void ignoresInvalidCoordinates() {
        response("{\"status\":\"1\",\"pois\":[" + POI.replace("106.429054,29.831753", "0,0") + "]}");
        assertThat(client.search("站点")).isEmpty();
    }
}
