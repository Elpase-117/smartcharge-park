package com.smartchargepark.reservation;
import com.smartchargepark.reservation.client.OrderFeignClient;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.dto.*;
import com.smartchargepark.reservation.model.Reservation;
import com.smartchargepark.reservation.service.*;
import com.smartchargepark.reservation.experiment.RollbackExperimentHook;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReservationCreationServiceTest {
    private final ReservationWriteService writer=mock(ReservationWriteService.class);
    private final StationRemoteService stations=mock(StationRemoteService.class);
    private final OrderFeignClient orders=mock(OrderFeignClient.class);
    private final RollbackExperimentHook hook=mock(RollbackExperimentHook.class);
    private final ReservationCreationService service=new ReservationCreationService(writer,stations,orders,new ReservationProperties(),hook,ReservationTestTime.at("17:00"));
    private final ReservationRequest request=new ReservationRequest(4L, ReservationTestTime.TODAY.plusDays(1),"19:00-20:00");
    private void prepare() {
        var station=new StationSummary(4L,"小鹏站",2,new BigDecimal("5.00"),new BigDecimal("0.35"));
        when(stations.detail(4L)).thenReturn(station);
        var row=new Reservation();row.setId(5L);row.setUserId(1L);row.setStationId(4L);row.setStatus("PENDING_PAYMENT");row.setReservationDate(request.reservationDate());row.setTimeSlot(request.timeSlot());
        when(writer.create(request,station,1L)).thenReturn(row);
    }
    @Test void orchestratesLocalBranchBeforeRemoteOrderAndKeepsLegacyResult() {
        prepare();when(orders.create(any())).thenReturn(new OrderResult(8L,"SCP123",5L,"PENDING_PAYMENT",new BigDecimal("5.35")));
        var result=service.create(request,1L);
        var ordered=inOrder(writer,orders,hook);ordered.verify(writer).create(eq(request),any(),eq(1L));ordered.verify(orders).create(new OrderCreateRequest(5L,1L,4L,new BigDecimal("5.35")));ordered.verify(hook).afterOrderCreated(5L,8L);
        assertThat(result.reservationId()).isEqualTo(5L);assertThat(result.orderId()).isEqualTo(8L);
        assertThat(result.orderStatus()).isEqualTo("PENDING_PAYMENT");
    }
    @Test void propagatesRemoteFailureInsteadOfReturningSuccess() {
        prepare();var failure=new IllegalStateException("unit-only remote failure");when(orders.create(any())).thenThrow(failure);
        assertThatThrownBy(()->service.create(request,1L)).isSameAs(failure);
        verifyNoInteractions(hook);
    }
    @Test void rejectsInvalidDateBeforeCallingWriteOrOrder() {
        assertThatThrownBy(()->service.create(new ReservationRequest(4L,ReservationTestTime.TODAY.minusDays(1),"19:00-20:00"),1L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(writer,orders,stations,hook);
    }
    @Test void propagatesExperimentFailureOnlyAfterBothBranchCallsReturn() {
        prepare();when(orders.create(any())).thenReturn(new OrderResult(8L,"SCP123",5L,"PENDING_PAYMENT",new BigDecimal("5.35")));
        var failure=new IllegalStateException("controlled unit experiment");
        doThrow(failure).when(hook).afterOrderCreated(5L,8L);
        assertThatThrownBy(()->service.create(request,1L)).isSameAs(failure);
        var ordered=inOrder(writer,orders,hook);
        ordered.verify(writer).create(eq(request),any(),eq(1L));
        ordered.verify(orders).create(any());
        ordered.verify(hook).afterOrderCreated(5L,8L);
    }
}
