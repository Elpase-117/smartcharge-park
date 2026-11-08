package com.smartchargepark.order;

import com.smartchargepark.common.web.CommonWebExceptionHandler;
import com.smartchargepark.order.controller.InternalOrderController;
import com.smartchargepark.order.dto.OrderResult;
import com.smartchargepark.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderCommonWebContractTest {
    private final OrderService orders = mock(OrderService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new InternalOrderController(orders)).setControllerAdvice(new CommonWebExceptionHandler()).build();

    @Test void internalOrderValidationUsesTheSamePublicErrorFields() throws Exception {
        mvc.perform(post("/internal/orders").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.path").value("/internal/orders"));
        verifyNoInteractions(orders);
    }

    @Test void internalOrderKeepsItsSuccessfulFeignDto() throws Exception {
        when(orders.create(any())).thenReturn(new OrderResult(10L, "SCP-unit-order", 9L,
                "PENDING_PAYMENT", new BigDecimal("1.50")));
        mvc.perform(post("/internal/orders").contentType("application/json")
                        .content("{\"reservationId\":9,\"userId\":2,\"stationId\":4,\"estimatedAmount\":1.50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.reservationId").value(9)).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.orderNo").value("SCP-unit-order")).andExpect(jsonPath("$.data").doesNotExist());
    }
}
