package com.smartchargepark.demo.service;

import com.smartchargepark.demo.dto.ReservationRequest;
import com.smartchargepark.demo.dto.ReservationResult;
import com.smartchargepark.demo.exception.BusinessException;
import com.smartchargepark.demo.mapper.DemoOrderMapper;
import com.smartchargepark.demo.mapper.ReservationMapper;
import com.smartchargepark.demo.mapper.StationMapper;
import com.smartchargepark.demo.model.DemoOrder;
import com.smartchargepark.demo.model.Reservation;
import com.smartchargepark.demo.model.Station;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ReservationService {
    private static final Long DEMO_USER_ID = 1L;

    private final StationMapper stationMapper;
    private final ReservationMapper reservationMapper;
    private final DemoOrderMapper orderMapper;

    public ReservationService(StationMapper stationMapper,
                              ReservationMapper reservationMapper,
                              DemoOrderMapper orderMapper) {
        this.stationMapper = stationMapper;
        this.reservationMapper = reservationMapper;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public ReservationResult create(ReservationRequest request) {
        if (!StationService.TIME_SLOTS.contains(request.timeSlot())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "预约时段不在可选范围内");
        }

        Station station = stationMapper.selectByIdForUpdate(request.stationId());
        if (station == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "站点不存在");
        }
        long reserved = reservationMapper.countActive(
                request.stationId(), request.reservationDate(), request.timeSlot());
        if (reserved >= station.getSlotCapacity()) {
            throw new BusinessException(HttpStatus.CONFLICT, "该预约时段容量已满");
        }

        LocalDateTime now = LocalDateTime.now();
        Reservation reservation = new Reservation();
        reservation.setUserId(DEMO_USER_ID);
        reservation.setStationId(request.stationId());
        reservation.setReservationDate(request.reservationDate());
        reservation.setTimeSlot(request.timeSlot());
        reservation.setStatus("PENDING_PAYMENT");
        reservation.setCreatedAt(now);
        reservationMapper.insert(reservation);

        BigDecimal amount = station.getParkingFee().add(station.getServiceFee());
        DemoOrder order = new DemoOrder();
        order.setOrderNo(buildOrderNo(now));
        order.setReservationId(reservation.getId());
        order.setUserId(DEMO_USER_ID);
        order.setStationId(request.stationId());
        order.setStatus("PENDING_PAYMENT");
        order.setEstimatedAmount(amount);
        order.setCreatedAt(now);
        orderMapper.insert(order);

        return new ReservationResult(
                reservation.getId(), reservation.getStatus(),
                order.getId(), order.getOrderNo(), order.getStatus(), order.getEstimatedAmount(),
                request.stationId(), request.reservationDate(), request.timeSlot()
        );
    }

    private String buildOrderNo(LocalDateTime now) {
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return "SCP" + timestamp + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}

