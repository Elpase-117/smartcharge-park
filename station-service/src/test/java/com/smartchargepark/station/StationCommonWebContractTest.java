package com.smartchargepark.station;

import com.smartchargepark.common.web.CommonWebExceptionHandler;
import com.smartchargepark.station.controller.StationController;
import com.smartchargepark.station.mapper.StationMapper;
import com.smartchargepark.station.model.Station;
import com.smartchargepark.station.service.StationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StationCommonWebContractTest {
    private final StationMapper mapper = mock(StationMapper.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new StationController(new StationQueryService(mapper)))
            .setControllerAdvice(new CommonWebExceptionHandler()).build();

    @Test void realStationLookupUsesPublicBusinessException() throws Exception {
        mvc.perform(get("/api/stations/99999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.code").value("STATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("站点不存在"))
                .andExpect(jsonPath("$.path").value("/api/stations/99999"));
    }

    @Test void stationListAndDetailKeepTheirOriginalShape() throws Exception {
        Station station = new Station(); station.setId(4L); station.setPowerSummary("超充 360 / 快充 250 kW");
        when(mapper.findAll(null)).thenReturn(List.of(station));
        when(mapper.findById(4L)).thenReturn(station);
        mvc.perform(get("/api/stations")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(4));
        mvc.perform(get("/api/stations/4")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.powerSummary").value("超充 360 / 快充 250 kW"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
