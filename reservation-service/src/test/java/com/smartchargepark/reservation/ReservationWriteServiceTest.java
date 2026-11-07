package com.smartchargepark.reservation;
import com.smartchargepark.reservation.dto.*;
import com.smartchargepark.reservation.mapper.*;
import com.smartchargepark.reservation.model.Reservation;
import com.smartchargepark.reservation.service.ReservationWriteService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReservationWriteServiceTest {
    @Test void locksBeforeCountingAndInserting() {
        var writer=mock(ReservationWriteMapper.class);var resources=mock(ReservationResourceMapper.class);
        when(writer.lockCreation()).thenReturn(1L);var request=new ReservationRequest(4L,LocalDate.now().plusDays(1),"19:00-20:00");
        when(resources.countActive(4L,request.reservationDate(),request.timeSlot())).thenReturn(1L);
        new ReservationWriteService(writer,resources).create(request,new StationSummary(4L,"站点",2),1L);
        var ordered=inOrder(writer,resources);ordered.verify(writer).lockCreation();ordered.verify(resources).countActive(4L,request.reservationDate(),request.timeSlot());ordered.verify(writer).insert(any(Reservation.class));
    }
    @Test void rejectsFullSlotWithoutInsert() {
        var writer=mock(ReservationWriteMapper.class);var resources=mock(ReservationResourceMapper.class);when(writer.lockCreation()).thenReturn(1L);
        var request=new ReservationRequest(4L,LocalDate.now().plusDays(1),"19:00-20:00");when(resources.countActive(4L,request.reservationDate(),request.timeSlot())).thenReturn(2L);
        assertThatThrownBy(()->new ReservationWriteService(writer,resources).create(request,new StationSummary(4L,"站点",2),1L)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(writer,never()).insert(any(Reservation.class));
    }
    @Test void persistsSecondAuthenticatedUserInsteadOfHardCodedOne() {
        var writer=mock(ReservationWriteMapper.class);var resources=mock(ReservationResourceMapper.class);
        when(writer.lockCreation()).thenReturn(1L);
        var request=new ReservationRequest(4L,LocalDate.now().plusDays(1),"14:00-15:00");
        var row=new ReservationWriteService(writer,resources).create(request,new StationSummary(4L,"站点",2),2L);
        assertThat(row.getUserId()).isEqualTo(2L);
    }}
