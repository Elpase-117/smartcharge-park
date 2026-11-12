package com.smartchargepark.reservation.service;
import com.smartchargepark.common.web.BusinessException;
import com.smartchargepark.reservation.client.OrderFeignClient;
import com.smartchargepark.reservation.config.ReservationProperties;
import com.smartchargepark.reservation.dto.*;
import com.smartchargepark.reservation.model.Reservation;
import com.smartchargepark.reservation.experiment.RollbackExperimentHook;
import feign.FeignException;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.apache.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Service
public class ReservationCreationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationCreationService.class);
    private final ReservationWriteService writer;
    private final StationRemoteService stations;
    private final OrderFeignClient orders;
    private final ReservationProperties properties;
    private final RollbackExperimentHook rollbackExperiment;
    private final ReservationTimePolicy timePolicy;
    public ReservationCreationService(ReservationWriteService writer, StationRemoteService stations,
                                      OrderFeignClient orders, ReservationProperties properties,
                                      RollbackExperimentHook rollbackExperiment, ReservationTimePolicy timePolicy) {
        this.writer = writer; this.stations = stations; this.orders = orders; this.properties = properties;
        this.rollbackExperiment = rollbackExperiment;
        this.timePolicy = timePolicy;
    }
    @GlobalTransactional(name = "create-reservation-and-order", rollbackFor = Exception.class)
    public ReservationResult create(ReservationRequest request, Long userId) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        LocalDateTime now = timePolicy.now();
        LocalDate today = now.toLocalDate();
        if (request.stationId() == null || request.stationId() <= 0 || request.reservationDate() == null
                || request.reservationDate().isBefore(today)
                || request.reservationDate().isAfter(today.plusDays(properties.getMaxDays()))
                || !timePolicy.isSupported(request.timeSlot())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "预约日期或时段不在可选范围内");
        }
        if (timePolicy.isExpired(request.reservationDate(), request.timeSlot(), now)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "RESERVATION_SLOT_EXPIRED",
                    "该预约时段已开始或已结束，请选择其他时段");
        }
        StationSummary station = stations.detail(request.stationId());
        if (station.parkingFee() == null || station.serviceFee() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "站点价格信息暂不可用");
        }
        // Separate Spring bean ensures phase-one local commit before the remote order branch.
        Reservation reservation = writer.create(request, station, userId);
        OrderCreateRequest orderRequest = new OrderCreateRequest(reservation.getId(), reservation.getUserId(),
                reservation.getStationId(), station.parkingFee().add(station.serviceFee()));
        // No success-shaped fallback: transport/business failures propagate to the global boundary.
        OrderResult order;
        try {
            order = orders.create(orderRequest);
        } catch (FeignException failure) {
            // Log classification only: raw Feign messages can include request/body details.
            log.warn("Order service call failed: status={} type={} cause={}", failure.status(),
                    failure.getClass().getSimpleName(),
                    failure.getCause() == null ? "none" : failure.getCause().getClass().getSimpleName());
            HttpStatusCode status = failure.status() >= 400 && failure.status() < 500
                    ? HttpStatusCode.valueOf(failure.status()) : HttpStatus.SERVICE_UNAVAILABLE;
            // Still an unchecked failure escaping the global transaction; never fabricate an order.
            throw new ResponseStatusException(status, "订单请求失败，请稍后重试");
        }
        if (order == null || order.id() == null || !reservation.getId().equals(order.reservationId())
                || !"PENDING_PAYMENT".equals(order.status()) || order.estimatedAmount() == null
                || order.estimatedAmount().compareTo(orderRequest.estimatedAmount()) != 0) {
            throw new IllegalStateException("订单服务未返回有效待支付订单");
        }
        log.info("[SeataTx] service=reservation-service xid={} phase=order-returned reservationId={} orderId={}",
                RootContext.getXID(), reservation.getId(), order.id());
        rollbackExperiment.afterOrderCreated(reservation.getId(), order.id());
        return new ReservationResult(reservation.getId(), reservation.getStatus(), order.id(), order.orderNo(),
                order.status(), order.estimatedAmount(), reservation.getStationId(), reservation.getReservationDate(), reservation.getTimeSlot());
    }
}
