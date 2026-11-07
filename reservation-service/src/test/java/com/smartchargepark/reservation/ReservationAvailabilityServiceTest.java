package com.smartchargepark.reservation;

import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.dto.AvailabilityItem;
import com.smartchargepark.reservation.dto.StationSummary;
import com.smartchargepark.reservation.mapper.ReservationResourceMapper;
import com.smartchargepark.reservation.service.ReservationAvailabilityService;
import com.smartchargepark.reservation.service.StationRemoteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReservationAvailabilityServiceTest {
    private final ReservationResourceMapper mapper = mock(ReservationResourceMapper.class);
    private final StationRemoteService stationRemoteService = mock(StationRemoteService.class);
    private final ReservationProperties properties = new ReservationProperties();
    private final ReservationAvailabilityService service = new ReservationAvailabilityService(mapper, stationRemoteService, properties);

    @Test
    void dateBeyondConfiguredWindowIsRejectedBeforeRemoteOrDatabaseCalls() {
        assertThatThrownBy(() -> service.availability(1L, LocalDate.now().plusDays(8)))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).isEqualTo("最多可提前预约未来 7 天");
                });
        verifyNoInteractions(mapper, stationRemoteService);
    }

    @Test
    void todayAndInclusiveLastDayRemainAvailable() {
        when(stationRemoteService.detail(1L)).thenReturn(new StationSummary(1L, "站点", 2));
        assertThat(service.availability(1L, LocalDate.now())).hasSize(3);
        assertThat(service.availability(1L, LocalDate.now().plusDays(7))).hasSize(3);
    }

    @Test
    void changedConfigurationIsUsedByExistingServiceIncludingErrorMessage() {
        properties.setMaxDays(14);
        when(stationRemoteService.detail(1L)).thenReturn(new StationSummary(1L, "站点", 2));
        assertThat(service.availability(1L, LocalDate.now().plusDays(10))).hasSize(3);
        assertThat(service.availability(1L, LocalDate.now().plusDays(14))).hasSize(3);
        assertThatThrownBy(() -> service.availability(1L, LocalDate.now().plusDays(15)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo("最多可提前预约未来 14 天"));
        properties.setMaxDays(7);
        assertThatThrownBy(() -> service.availability(1L, LocalDate.now().plusDays(10)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo("最多可提前预约未来 7 天"));
    }

    @Test
    void pastDateIsRejectedBeforeAnyDatabaseQuery() {
        assertThatThrownBy(() -> service.availability(1L, LocalDate.now().minusDays(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(mapper, stationRemoteService);
    }

    @Test
    void unknownStationReturnsNotFoundInsteadOfInventingCapacity() {
        when(stationRemoteService.detail(99999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "站点不存在"));
        assertThatThrownBy(() -> service.availability(99999L, LocalDate.now().plusDays(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(mapper);
    }

    @Test
    void providerUnavailableDoesNotQueryOrInventReservationCapacity() {
        when(stationRemoteService.detail(1L))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用"));
        assertThatThrownBy(() -> service.availability(1L, LocalDate.now().plusDays(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(mapper);
    }

    @Test
    void countsReservationsForEachSlotIndependently() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(stationRemoteService.detail(1L)).thenReturn(new StationSummary(1L, "站点", 2));
        when(mapper.countActive(1L, date, "09:00-10:00")).thenReturn(2L);
        when(mapper.countActive(1L, date, "14:00-15:00")).thenReturn(1L);

        assertThat(service.availability(1L, date)).containsExactly(
                new AvailabilityItem("09:00-10:00", 2, 2, 0),
                new AvailabilityItem("14:00-15:00", 2, 1, 1),
                new AvailabilityItem("19:00-20:00", 2, 0, 2));
    }

    @Test
    void remainingCapacityNeverBecomesNegative() {
        LocalDate date = LocalDate.now().plusDays(1);
        when(stationRemoteService.detail(1L)).thenReturn(new StationSummary(1L, "站点", 2));
        when(mapper.countActive(1L, date, "09:00-10:00")).thenReturn(3L);

        assertThat(service.availability(1L, date).get(0).remaining()).isZero();
    }
}
