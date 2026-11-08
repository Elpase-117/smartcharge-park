package com.smartchargepark.reservation;

import com.smartchargepark.common.web.CommonWebExceptionHandler;
import com.smartchargepark.reservation.client.OrderFeignClient;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.controller.ReservationController;
import com.smartchargepark.reservation.dto.StationSummary;
import com.smartchargepark.reservation.experiment.RollbackExperimentHook;
import com.smartchargepark.reservation.model.Reservation;
import com.smartchargepark.reservation.service.*;
import feign.FeignException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderFailurePropagationTest {
    @ParameterizedTest
    @ValueSource(ints = {-1, 400, 401, 403, 404, 409, 429, 500, 503})
    void orderFailureEscapesOrchestrationAsSafeErrorAfterLocalWrite(int downstreamStatus) throws Exception {
        var writer = mock(ReservationWriteService.class);
        var stations = mock(StationRemoteService.class);
        var orders = mock(OrderFeignClient.class);
        var hook = mock(RollbackExperimentHook.class);
        var users = mock(CurrentUserService.class);
        var service = new ReservationCreationService(writer, stations, orders, new ReservationProperties(), hook);
        var station = new StationSummary(4L, "测试站点", 2, BigDecimal.ONE, BigDecimal.ONE);
        when(stations.detail(4L)).thenReturn(station);
        var reservation = new Reservation();
        reservation.setId(99L); reservation.setUserId(2L); reservation.setStationId(4L);
        when(writer.create(any(), eq(station), eq(2L))).thenReturn(reservation);
        when(users.requireUserId(anyString(), isNull(), isNull())).thenReturn(2L);
        var failure = mock(FeignException.class);
        when(failure.status()).thenReturn(downstreamStatus);
        when(failure.getMessage()).thenReturn("private SQL token http://internal-provider");
        when(orders.create(any())).thenThrow(failure);
        var mvc = MockMvcBuilders.standaloneSetup(new ReservationController(service, users))
                .setControllerAdvice(new CommonWebExceptionHandler()).build();
        int expected = downstreamStatus >= 400 && downstreamStatus < 500 ? downstreamStatus : 503;
        String body = "{\"stationId\":4,\"reservationDate\":\"" + LocalDate.now().plusDays(1)
                + "\",\"timeSlot\":\"19:00-20:00\"}";
        var result = mvc.perform(post("/api/reservations").contentType("application/json")
                        .content(body).header("Authorization", "Bearer unit-marker"))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.status").value(expected))
                .andExpect(jsonPath("$.code").isString()).andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.path").value("/api/reservations"))
                .andExpect(jsonPath("$.orderId").doesNotExist()).andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();
        // MVC sees the unchecked exception only after it has escaped the annotated service method.
        assertThat(result.getResolvedException()).isInstanceOf(ResponseStatusException.class);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("private", "SQL", "token", "internal-provider", "FeignException", "stackTrace");
        var sequence = inOrder(writer, orders);
        sequence.verify(writer).create(any(), eq(station), eq(2L));
        sequence.verify(orders).create(any());
        verifyNoInteractions(hook);
    }
}
