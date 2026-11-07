package com.smartchargepark.reservation.service;
import com.smartchargepark.reservation.dto.ReservationRequest;
import com.smartchargepark.reservation.dto.StationSummary;
import com.smartchargepark.reservation.mapper.*;
import com.smartchargepark.reservation.model.Reservation;
import org.apache.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
@Service
public class ReservationWriteService {
    private static final Logger log = LoggerFactory.getLogger(ReservationWriteService.class);
    private final ReservationWriteMapper writer;
    private final ReservationResourceMapper resources;
    public ReservationWriteService(ReservationWriteMapper writer, ReservationResourceMapper resources) {
        this.writer = writer; this.resources = resources;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Reservation create(ReservationRequest request, StationSummary station, Long userId) {
        // Coarse database mutex: held only across capacity check and insert, never across RPC.
        // This preserves correctness across instances without maintaining a second capacity counter.
        if (writer.lockCreation() == null) { throw new IllegalStateException("预约写入互斥行缺失"); }
        long reserved = resources.countActive(request.stationId(), request.reservationDate(), request.timeSlot());
        if (reserved >= station.slotCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该预约时段容量已满");
        }
        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setStationId(request.stationId());
        reservation.setReservationDate(request.reservationDate());
        reservation.setTimeSlot(request.timeSlot());
        reservation.setStatus("PENDING_PAYMENT");
        reservation.setCreatedAt(LocalDateTime.now());
        writer.insert(reservation);
        log.info("[SeataTx] service=reservation-service xid={} phase=reservation-inserted reservationId={}",
                RootContext.getXID(), reservation.getId());
        return reservation;
    }
}
