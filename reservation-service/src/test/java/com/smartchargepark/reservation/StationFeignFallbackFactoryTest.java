package com.smartchargepark.reservation;

import com.smartchargepark.reservation.client.StationFeignFallbackFactory;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.mapper.ReservationResourceMapper;
import com.smartchargepark.reservation.service.ReservationAvailabilityService;
import com.smartchargepark.reservation.service.StationRemoteService;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StationFeignFallbackFactoryTest {
    private final StationFeignFallbackFactory factory = new StationFeignFallbackFactory();

    @Test
    void connectionFailureThrows503InsteadOfReturningStationData() {
        assertThatThrownBy(() -> factory.create(new ConnectException()).getStation(4L))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getReason()).isEqualTo("站点服务暂不可用，请稍后重试");
                });
    }

    @Test
    void provider404IsPreservedForExistingBusinessMapping() {
        FeignException.NotFound missing = mock(FeignException.NotFound.class);
        assertThatThrownBy(() -> factory.create(missing).getStation(99999L)).isSameAs(missing);
    }

    @Test
    void fallbackStopsAvailabilityBeforeAnyReservationQuery() {
        ReservationResourceMapper mapper = mock(ReservationResourceMapper.class);
        StationRemoteService remote = new StationRemoteService(factory.create(new ConnectException()));
        ReservationAvailabilityService service = new ReservationAvailabilityService(mapper, remote, new ReservationProperties());

        assertThatThrownBy(() -> service.availability(4L, LocalDate.now().plusDays(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(mapper);
    }
}
