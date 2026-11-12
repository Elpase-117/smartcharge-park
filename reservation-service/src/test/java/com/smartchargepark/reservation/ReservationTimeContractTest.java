package com.smartchargepark.reservation;

import com.smartchargepark.common.web.CommonWebExceptionHandler;
import com.smartchargepark.reservation.client.OrderFeignClient;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.controller.ReservationController;
import com.smartchargepark.reservation.controller.ReservationResourceController;
import com.smartchargepark.reservation.dto.OrderResult;
import com.smartchargepark.reservation.dto.ReservationRequest;
import com.smartchargepark.reservation.dto.StationSummary;
import com.smartchargepark.reservation.experiment.RollbackExperimentHook;
import com.smartchargepark.reservation.mapper.ReservationResourceMapper;
import com.smartchargepark.reservation.model.Reservation;
import com.smartchargepark.reservation.service.CurrentUserService;
import com.smartchargepark.reservation.service.ReservationAvailabilityService;
import com.smartchargepark.reservation.service.ReservationCreationService;
import com.smartchargepark.reservation.service.ReservationWriteService;
import com.smartchargepark.reservation.service.StationRemoteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationTimeContractTest {
    private final ReservationProperties properties = new ReservationProperties();
    private final ReservationResourceMapper mapper = mock(ReservationResourceMapper.class);
    private final ReservationWriteService writer = mock(ReservationWriteService.class);
    private final StationRemoteService stations = mock(StationRemoteService.class);
    private final OrderFeignClient orders = mock(OrderFeignClient.class);
    private final RollbackExperimentHook hook = mock(RollbackExperimentHook.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final StationSummary station = new StationSummary(4L, "测试站点", 2, BigDecimal.ONE, BigDecimal.ONE);

    private MockMvc mvcAt(String time) {
        var policy = ReservationTestTime.at(time);
        var availability = new ReservationAvailabilityService(mapper, stations, properties, policy);
        var creation = new ReservationCreationService(writer, stations, orders, properties, hook, policy);
        when(users.requireUserId(any(), any(), any())).thenReturn(1L);
        return MockMvcBuilders.standaloneSetup(new ReservationResourceController(availability, properties),
                        new ReservationController(creation, users))
                .setControllerAdvice(new CommonWebExceptionHandler()).build();
    }

    @Test
    void todayAtSeventeenReturnsExpiredFlagsWithoutFakingRemainingCapacity() throws Exception {
        when(stations.detail(4L)).thenReturn(station);
        when(mapper.countActive(4L, ReservationTestTime.TODAY, "14:00-15:00")).thenReturn(1L);
        mvcAt("17:00").perform(get("/api/reservations/availability")
                        .param("stationId", "4").param("date", ReservationTestTime.TODAY.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].expired").value(true))
                .andExpect(jsonPath("$[0].capacity").value(2))
                .andExpect(jsonPath("$[0].reserved").value(0))
                .andExpect(jsonPath("$[0].remaining").value(2))
                .andExpect(jsonPath("$[1].expired").value(true))
                .andExpect(jsonPath("$[1].remaining").value(1))
                .andExpect(jsonPath("$[2].expired").value(false))
                .andExpect(jsonPath("$[2].remaining").value(2));
    }

    @ParameterizedTest
    @CsvSource({"08:59,0,false", "09:00,0,true", "13:59,1,false", "14:00,1,true"})
    void availabilityChangesExactlyAtTheSlotStart(String time, int slotIndex, boolean expired) throws Exception {
        when(stations.detail(4L)).thenReturn(station);
        mvcAt(time).perform(get("/api/reservations/availability")
                        .param("stationId", "4").param("date", ReservationTestTime.TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[" + slotIndex + "].expired").value(expired))
                .andExpect(jsonPath("$[" + slotIndex + "].remaining").value(2));
    }

    @Test
    void tomorrowHasNoExpiredSlotsEvenWhenTodayIsSeventeen() throws Exception {
        when(stations.detail(4L)).thenReturn(station);
        mvcAt("17:00").perform(get("/api/reservations/availability")
                        .param("stationId", "4").param("date", ReservationTestTime.TODAY.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expired").value(false))
                .andExpect(jsonPath("$[1].expired").value(false))
                .andExpect(jsonPath("$[2].expired").value(false))
                .andExpect(jsonPath("$[0].remaining").value(2))
                .andExpect(jsonPath("$[1].remaining").value(2))
                .andExpect(jsonPath("$[2].remaining").value(2));
    }

    @Test
    void aFullFutureSlotRemainsDifferentFromAnExpiredSlot() throws Exception {
        when(stations.detail(4L)).thenReturn(station);
        when(mapper.countActive(4L, ReservationTestTime.TODAY, "19:00-20:00")).thenReturn(2L);
        mvcAt("17:00").perform(get("/api/reservations/availability")
                        .param("stationId", "4").param("date", ReservationTestTime.TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expired").value(true))
                .andExpect(jsonPath("$[0].remaining").value(2))
                .andExpect(jsonPath("$[2].expired").value(false))
                .andExpect(jsonPath("$[2].remaining").value(0));
    }

    @ParameterizedTest
    @CsvSource({"17:00,09:00-10:00", "17:00,14:00-15:00", "09:00,09:00-10:00",
            "14:00,14:00-15:00", "19:00,19:00-20:00"})
    void directExpiredPostsReturn400BeforeWritingOrCallingOrder(String time, String slot) throws Exception {
        mvcAt(time).perform(post("/api/reservations").contentType("application/json")
                        .content(body(ReservationTestTime.TODAY, slot)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_SLOT_EXPIRED"))
                .andExpect(jsonPath("$.message").value("该预约时段已开始或已结束，请选择其他时段"))
                .andExpect(jsonPath("$.reservationId").doesNotExist());
        verifyNoInteractions(writer, orders, stations, hook);
    }

    @ParameterizedTest
    @CsvSource({"17:00,0,19:00-20:00", "08:59,0,09:00-10:00", "13:59,0,14:00-15:00",
            "17:00,1,09:00-10:00"})
    void futureStartsAndFutureDatesKeepTheOriginalCreationFlow(String time, int days, String slot) throws Exception {
        LocalDate date = ReservationTestTime.TODAY.plusDays(days);
        ReservationRequest request = new ReservationRequest(4L, date, slot);
        when(stations.detail(4L)).thenReturn(station);
        Reservation row = new Reservation();
        row.setId(99L); row.setUserId(1L); row.setStationId(4L); row.setStatus("PENDING_PAYMENT");
        row.setReservationDate(date); row.setTimeSlot(slot);
        when(writer.create(request, station, 1L)).thenReturn(row);
        when(orders.create(any())).thenReturn(new OrderResult(42L, "SCP-unit-99", 99L,
                "PENDING_PAYMENT", new BigDecimal("2.00")));
        mvcAt(time).perform(post("/api/reservations").contentType("application/json").content(body(date, slot)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reservationId").value(99))
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"));
        var sequence = inOrder(writer, orders, hook);
        sequence.verify(writer).create(request, station, 1L);
        sequence.verify(orders).create(any());
        sequence.verify(hook).afterOrderCreated(99L, 42L);
    }

    @Test
    void pastDatesStillReturn400UsingTheSameBusinessClock() throws Exception {
        mvcAt("00:15").perform(post("/api/reservations").contentType("application/json")
                        .content(body(ReservationTestTime.TODAY.minusDays(1), "19:00-20:00")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(writer, orders, stations, hook);
    }

    @Test
    void theSameTimePolicyKeepsTheSevenToThreeToSevenDayWindowDynamic() throws Exception {
        when(stations.detail(4L)).thenReturn(station);
        var mvc = mvcAt("17:00");
        String date = ReservationTestTime.TODAY.plusDays(7).toString();
        properties.setMaxDays(7);
        mvc.perform(get("/api/reservations/availability").param("stationId", "4").param("date", date))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].expired").value(false));
        properties.setMaxDays(3);
        mvc.perform(get("/api/reservations/availability").param("stationId", "4").param("date", date))
                .andExpect(status().isBadRequest());
        properties.setMaxDays(7);
        mvc.perform(get("/api/reservations/availability").param("stationId", "4").param("date", date))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].expired").value(false));
    }

    private String body(LocalDate date, String slot) {
        return "{\"stationId\":4,\"reservationDate\":\"" + date + "\",\"timeSlot\":\"" + slot + "\"}";
    }
}
