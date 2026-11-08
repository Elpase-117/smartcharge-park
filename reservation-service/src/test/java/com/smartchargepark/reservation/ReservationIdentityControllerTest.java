package com.smartchargepark.reservation;

import com.smartchargepark.reservation.controller.ReservationController;
import com.smartchargepark.reservation.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationIdentityControllerTest {
    private final ReservationCreationService creation = mock(ReservationCreationService.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final String body = "{\"stationId\":4,\"reservationDate\":\"" + java.time.LocalDate.now().plusDays(1)
            + "\",\"timeSlot\":\"19:00-20:00\",\"userId\":999}";

    @Test void usesAuthenticatedIdentityAndIgnoresClientSuppliedUserId() throws Exception {
        when(users.requireUserId("Bearer unit-token", null, null)).thenReturn(2L);
        var mvc = MockMvcBuilders.standaloneSetup(new ReservationController(creation, users)).build();
        mvc.perform(post("/api/reservations").contentType("application/json")
                .header("Authorization", "Bearer unit-token").content(body)).andExpect(status().isOk());
        var sequence = inOrder(users, creation);
        sequence.verify(users).requireUserId("Bearer unit-token", null, null);
        sequence.verify(creation).create(any(), eq(2L));
    }
    @Test void missingIdentityCannotEnterGlobalTransaction() throws Exception {
        when(users.requireUserId(null, null, null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var mvc = MockMvcBuilders.standaloneSetup(new ReservationController(creation, users)).build();
        mvc.perform(post("/api/reservations").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(creation);
    }
}
