package com.smartchargepark.reservation;

import com.smartchargepark.reservation.client.StationFeignClient;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.dto.StationSummary;
import com.smartchargepark.reservation.dto.AvailabilityItem;
import com.smartchargepark.reservation.mapper.ReservationResourceMapper;
import com.smartchargepark.reservation.service.ReservationAvailabilityService;
import com.smartchargepark.reservation.service.StationRemoteService;
import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StationFeignServiceTest {
    private final StationFeignClient client = mock(StationFeignClient.class);
    private final StationRemoteService service = new StationRemoteService(client);

    @Test
    void usesFeignClientAndKeepsRequiredStationFields() {
        StationSummary station = new StationSummary(4L, "小鹏站", 2);
        when(client.getStation(4L)).thenReturn(station);

        assertThat(service.detail(4L)).isEqualTo(station);
        verify(client).getStation(4L);
    }

    @Test
    void remote404KeepsNotFoundMessage() {
        when(client.getStation(4L)).thenThrow(httpError(404));
        assertStatus(HttpStatus.NOT_FOUND, "站点不存在");
    }

    @Test
    void noHealthyProviderReturns503() {
        when(client.getStation(4L)).thenThrow(httpError(503));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用，请稍后重试");
    }

    @Test
    void connectionOrReadFailureReturns503() {
        when(client.getStation(4L)).thenThrow(mock(RetryableException.class));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用，请稍后重试");
    }

    @Test
    void missingCapacityDoesNotInventAValue() {
        when(client.getStation(4L)).thenReturn(new StationSummary(4L, "小鹏站", null));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用，请稍后重试");
    }

    @Test
    void emptyResponseReturns503() {
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用，请稍后重试");
    }

    @Test
    void mismatchedStationIdReturns503() {
        when(client.getStation(4L)).thenReturn(new StationSummary(5L, "其他站点", 2));
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, "站点服务暂不可用，请稍后重试");
    }

    @Test
    void feignFailureStopsReservationQueries() {
        ReservationResourceMapper mapper = mock(ReservationResourceMapper.class);
        ReservationAvailabilityService availability = new ReservationAvailabilityService(mapper, service, new ReservationProperties());
        when(client.getStation(4L)).thenThrow(httpError(503));

        assertThatThrownBy(() -> availability.availability(4L, LocalDate.now().plusDays(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(mapper);
    }

    @Test
    void availabilityUsesCapacityReturnedByFeign() {
        ReservationResourceMapper mapper = mock(ReservationResourceMapper.class);
        ReservationAvailabilityService availability = new ReservationAvailabilityService(mapper, service, new ReservationProperties());
        LocalDate date = LocalDate.now().plusDays(1);
        when(client.getStation(4L)).thenReturn(new StationSummary(4L, "小鹏站", 5));
        when(mapper.countActive(4L, date, "09:00-10:00")).thenReturn(2L);

        assertThat(availability.availability(4L, date)).containsExactly(
                new AvailabilityItem("09:00-10:00", 5, 2, 3),
                new AvailabilityItem("14:00-15:00", 5, 0, 5),
                new AvailabilityItem("19:00-20:00", 5, 0, 5));
    }

    private FeignException httpError(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://station-service/api/stations/4",
                Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("StationFeignClient#getStation", Response.builder()
                .request(request).status(status).reason("Provider error").headers(Map.of()).build());
    }

    private void assertStatus(HttpStatus expected, String reason) {
        assertThatThrownBy(() -> service.detail(4L)).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
            assertThat(ex.getStatusCode()).isEqualTo(expected);
            assertThat(ex.getReason()).isEqualTo(reason);
        });
    }
}
