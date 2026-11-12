package com.smartchargepark.station.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartchargepark.station.controller.StationController;
import com.smartchargepark.station.mapper.StationMapper;
import com.smartchargepark.station.model.Station;
import com.smartchargepark.station.service.StationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AmapOptionalConfigurationTest {
    @Test void missingKeyDoesNotPreventStationBeansOrOriginalQueries() {
        var mapper = mock(StationMapper.class);
        var station = new Station(); station.setId(4L); station.setName("小鹏充电站");
        when(mapper.findAll(null)).thenReturn(List.of(station)); when(mapper.findById(4L)).thenReturn(station);
        when(mapper.findAll("小鹏")).thenReturn(List.of(station));
        new ApplicationContextRunner().withBean(AmapProperties.class).withBean(ObjectMapper.class)
                .withBean(AmapPoiClient.class).withBean(AmapPoiMatcher.class).withBean(StationMapper.class, () -> mapper)
                .withBean(AmapStationSyncService.class).withBean(StationQueryService.class).withBean(StationController.class)
                .run(context -> {
                    assertThat(context).hasNotFailed(); assertThat(context.getBean(AmapProperties.class).isConfigured()).isFalse();
                    var mvc = MockMvcBuilders.standaloneSetup(context.getBean(StationController.class)).build();
                    mvc.perform(get("/api/stations")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(4));
                    mvc.perform(get("/api/stations/4")).andExpect(status().isOk()).andExpect(jsonPath("$.amapPoiId").isEmpty());
                    mvc.perform(get("/api/stations").param("keyword", "小鹏")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("小鹏充电站"));
                    verify(mapper, never()).updatePoi(any(), any(), any());
                });
    }
}
