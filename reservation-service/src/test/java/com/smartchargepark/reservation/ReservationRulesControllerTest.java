package com.smartchargepark.reservation;

import com.smartchargepark.common.web.CommonWebExceptionHandler;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.controller.ReservationResourceController;
import com.smartchargepark.reservation.dto.StationSummary;
import com.smartchargepark.reservation.mapper.ReservationResourceMapper;
import com.smartchargepark.reservation.service.ReservationAvailabilityService;
import com.smartchargepark.reservation.service.StationRemoteService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationRulesControllerTest {
    private final ReservationProperties properties = new ReservationProperties();
    private final ReservationResourceMapper mapper = mock(ReservationResourceMapper.class);
    private final StationRemoteService stations = mock(StationRemoteService.class);
    private final ReservationAvailabilityService availability =
            new ReservationAvailabilityService(mapper, stations, properties, ReservationTestTime.at("17:00"));
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ReservationResourceController(availability, properties))
            .setControllerAdvice(new CommonWebExceptionHandler()).build();

    @Test
    void returnsTheCurrentConfiguredWindow() throws Exception {
        properties.setMaxDays(7);
        mvc.perform(get("/api/reservations/rules"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxDays").value(7));
    }

    @Test
    void nextRequestReadsChangedPropertiesFromTheSameController() throws Exception {
        properties.setMaxDays(7);
        mvc.perform(get("/api/reservations/rules")).andExpect(jsonPath("$.maxDays").value(7));
        properties.setMaxDays(3);
        mvc.perform(get("/api/reservations/rules")).andExpect(jsonPath("$.maxDays").value(3));
    }

    @Test
    void availabilityKeepsItsInclusiveUpperBoundAfterTheWindowShrinks() throws Exception {
        when(stations.detail(1L)).thenReturn(new StationSummary(1L, "站点", 2));
        properties.setMaxDays(7);
        requestAvailability(7).andExpect(status().isOk());
        requestAvailability(8).andExpect(status().isBadRequest());
        properties.setMaxDays(3);
        requestAvailability(3).andExpect(status().isOk()).andExpect(jsonPath("$[0].capacity").value(2));
        requestAvailability(4).andExpect(status().isBadRequest());
    }

    @Test
    void zeroDayWindowAllowsTodayAndRejectsTomorrow() throws Exception {
        properties.setMaxDays(0);
        when(stations.detail(1L)).thenReturn(new StationSummary(1L, "站点", 2));
        mvc.perform(get("/api/reservations/rules")).andExpect(jsonPath("$.maxDays").value(0));
        requestAvailability(0).andExpect(status().isOk());
        requestAvailability(1).andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions requestAvailability(int days) throws Exception {
        return mvc.perform(get("/api/reservations/availability")
                .param("stationId", "1").param("date", ReservationTestTime.TODAY.plusDays(days).toString()));
    }
}
