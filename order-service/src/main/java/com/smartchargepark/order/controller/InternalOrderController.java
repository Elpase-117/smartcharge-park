package com.smartchargepark.order.controller;
import com.smartchargepark.order.dto.*;
import com.smartchargepark.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {
    private final OrderService service;
    public InternalOrderController(OrderService service) { this.service = service; }
    @PostMapping
    public OrderResult create(@Valid @RequestBody OrderCreateRequest request) { return service.create(request); }
}
