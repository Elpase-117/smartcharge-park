package com.smartchargepark.reservation;

import com.smartchargepark.reservation.client.UserFeignClient;
import com.smartchargepark.reservation.dto.CurrentUser;
import com.smartchargepark.reservation.service.CurrentUserService;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserServiceTest {
    private final UserFeignClient client = mock(UserFeignClient.class);
    private final CurrentUserService service = new CurrentUserService(client);

    @Test void resolvesIdentityFromUserServiceRatherThanFixedDemoId() {
        when(client.current("Bearer unit-token")).thenReturn(new CurrentUser(2L, "第二用户", "DRIVER"));
        assertThat(service.requireUserId("Bearer unit-token")).isEqualTo(2L);
    }
    @Test void rejectsMissingAuthorizationWithoutRemoteCall() {
        assertThatThrownBy(() -> service.requireUserId(null)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(client);
    }
    @Test void preservesUserServiceUnauthorized() {
        var error = mock(FeignException.class); when(error.status()).thenReturn(401);
        when(client.current(anyString())).thenThrow(error);
        assertThatThrownBy(() -> service.requireUserId("Bearer invalid")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
    }
    @Test void failsClosedOnUserServiceOutage() {
        var error = mock(FeignException.class); when(error.status()).thenReturn(503);
        when(client.current(anyString())).thenThrow(error);
        assertThatThrownBy(() -> service.requireUserId("Bearer unit-token")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
    }
    @Test void rejectsMalformedRemoteIdentity() {
        when(client.current(anyString())).thenReturn(new CurrentUser(null, "", ""));
        assertThatThrownBy(() -> service.requireUserId("Bearer unit-token")).isInstanceOf(ResponseStatusException.class);
    }
}
