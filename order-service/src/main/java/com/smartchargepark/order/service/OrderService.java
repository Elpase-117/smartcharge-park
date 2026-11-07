package com.smartchargepark.order.service;
import com.smartchargepark.order.dto.*;
import com.smartchargepark.order.mapper.OrderMapper;
import com.smartchargepark.order.model.DemoOrder;
import org.apache.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderMapper mapper;
    public OrderService(OrderMapper mapper) { this.mapper = mapper; }
    @Transactional(rollbackFor = Exception.class)
    public OrderResult create(OrderCreateRequest request) {
        DemoOrder order = new DemoOrder();
        LocalDateTime now = LocalDateTime.now();
        order.setOrderNo("SCP" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(100, 1000));
        order.setReservationId(request.reservationId());
        order.setUserId(request.userId());
        order.setStationId(request.stationId());
        order.setStatus("PENDING_PAYMENT");
        order.setEstimatedAmount(request.estimatedAmount());
        order.setCreatedAt(now);
        mapper.insert(order);
        log.info("[SeataTx] service=order-service xid={} phase=order-inserted reservationId={} orderId={}",
                RootContext.getXID(), order.getReservationId(), order.getId());
        return new OrderResult(order.getId(), order.getOrderNo(), order.getReservationId(), order.getStatus(), order.getEstimatedAmount());
    }
}
