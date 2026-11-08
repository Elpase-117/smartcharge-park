package com.smartchargepark.reservation;

import com.smartchargepark.common.web.CommonWebExceptionHandler;
import com.smartchargepark.reservation.client.StationFeignFallbackFactory;
import com.smartchargepark.reservation.controller.ReservationController;
import com.smartchargepark.reservation.controller.ReservationResourceController;
import com.smartchargepark.reservation.dto.AvailabilityItem;
import com.smartchargepark.reservation.dto.ReservationResult;
import com.smartchargepark.reservation.service.CurrentUserService;
import com.smartchargepark.reservation.service.ReservationAvailabilityService;
import com.smartchargepark.reservation.service.ReservationCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationCommonWebContractTest {
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final ReservationCreationService creation = mock(ReservationCreationService.class);
    private final ReservationAvailabilityService availability = mock(ReservationAvailabilityService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ReservationController(creation, users), new ReservationResourceController(availability))
            .setControllerAdvice(new CommonWebExceptionHandler()).build();
    private final LocalDate date = LocalDate.now().plusDays(1);
    private final String body = "{\"stationId\":4,\"reservationDate\":\"" + date + "\",\"timeSlot\":\"19:00-20:00\"}";

    @Test void identityConflictRetains403AndCannotStartGlobalBusiness() throws Exception {
        when(users.requireUserId("Bearer unit-marker", "1", "ADMIN"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        mvc.perform(post("/api/reservations").contentType("application/json").content(body)
                        .header("Authorization", "Bearer unit-marker").header("X-Authenticated-User-Id", "1")
                        .header("X-Authenticated-User-Role", "ADMIN"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/reservations"));
        verifyNoInteractions(creation);
    }

    @Test void actualStationFeignFallbackRetains503ThroughSharedAdvice() throws Exception {
        Throwable fallback = catchThrowable(() -> new StationFeignFallbackFactory()
                .create(new IllegalStateException("private-provider-marker")).getStation(4L));
        when(availability.availability(4L, date)).thenThrow(fallback);
        mvc.perform(get("/api/reservations/availability").param("stationId", "4").param("date", date.toString()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test void orchestrationFailureEscapesBusinessAndBecomesHttp500WithoutDetails() throws Exception {
        when(users.requireUserId("Bearer unit-marker", null, null)).thenReturn(2L);
        var failure = new IllegalStateException("SELECT password FROM private_table C:\\private\\app");
        when(creation.create(any(), eq(2L))).thenThrow(failure);
        var result = mvc.perform(post("/api/reservations").contentType("application/json").content(body)
                        .header("Authorization", "Bearer unit-marker"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(result.getResolvedException()).isSameAs(failure);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("private", "SELECT", "IllegalStateException");
        var sequence = inOrder(users, creation);
        sequence.verify(users).requireUserId("Bearer unit-marker", null, null);
        sequence.verify(creation).create(any(), eq(2L));
    }

    @Test void availabilityAndReservationSuccessDtosAreNotWrapped() throws Exception {
        when(availability.availability(4L, date)).thenReturn(List.of(new AvailabilityItem("19:00-20:00", 2, 0, 2)));
        mvc.perform(get("/api/reservations/availability").param("stationId", "4").param("date", date.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].timeSlot").value("19:00-20:00"))
                .andExpect(jsonPath("$[0].remaining").value(2));
        when(users.requireUserId("Bearer unit-marker", null, null)).thenReturn(2L);
        when(creation.create(any(), eq(2L))).thenReturn(new ReservationResult(10L, "CONFIRMED", 11L,
                "SCP-unit-order", "PENDING_PAYMENT", BigDecimal.ZERO, 4L, date, "19:00-20:00"));
        mvc.perform(post("/api/reservations").contentType("application/json").content(body)
                        .header("Authorization", "Bearer unit-marker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reservationId").value(10))
                .andExpect(jsonPath("$.orderId").value(11)).andExpect(jsonPath("$.orderNo").value("SCP-unit-order"))
                .andExpect(jsonPath("$.estimatedAmount").value(0)).andExpect(jsonPath("$.data").doesNotExist());
    }
}
