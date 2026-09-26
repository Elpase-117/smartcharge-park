package com.smartchargepark.demo;

import com.smartchargepark.demo.dto.ReservationRequest;
import com.smartchargepark.demo.dto.ReservationResult;
import com.smartchargepark.demo.exception.BusinessException;
import com.smartchargepark.demo.mapper.DemoOrderMapper;
import com.smartchargepark.demo.mapper.ReservationMapper;
import com.smartchargepark.demo.mapper.StationMapper;
import com.smartchargepark.demo.model.Station;
import com.smartchargepark.demo.service.ReservationService;
import org.junit.jupiter.api.Test;
import com.smartchargepark.demo.model.Reservation;
import com.smartchargepark.demo.model.DemoOrder;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationServiceTest {
    @Test
    void rejectsReservationBeforeCurrentDate() {
        StationMapper stationMapper = mock(StationMapper.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        DemoOrderMapper orderMapper = mock(DemoOrderMapper.class);
        ReservationService service = new ReservationService(stationMapper, reservationMapper, orderMapper);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> service.create(new ReservationRequest(
                        1L, LocalDate.now().minusDays(1), "09:00-10:00"))
        );

        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception).hasMessage("预约日期不能早于当前日期");
        verifyNoInteractions(stationMapper, reservationMapper, orderMapper);
    }

    @Test
    void createsReservationAndPendingOrderWhenCapacityIsAvailable() {
        StationMapper stationMapper = mock(StationMapper.class);
        ReservationMapper reservationMapper = mock(ReservationMapper.class);
        DemoOrderMapper orderMapper = mock(DemoOrderMapper.class);

        Station station = new Station();
        station.setId(1L);
        station.setSlotCapacity(2);
        station.setParkingFee(new BigDecimal("5.00"));
        station.setServiceFee(new BigDecimal("0.35"));
        when(stationMapper.selectByIdForUpdate(1L)).thenReturn(station);
        when(reservationMapper.countActive(any(), any(), any())).thenReturn(0L);

        ReservationService service = new ReservationService(stationMapper, reservationMapper, orderMapper);
        ReservationResult result = service.create(new ReservationRequest(
                1L, LocalDate.now().plusDays(1), "09:00-10:00"));

        assertThat(result.reservationStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.orderStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.estimatedAmount()).isEqualByComparingTo("5.35");
        verify(reservationMapper).insert(any(Reservation.class));
        verify(orderMapper).insert(any(DemoOrder.class));
    }
}
