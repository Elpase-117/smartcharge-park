package com.smartchargepark.user;

import com.smartchargepark.common.web.CommonWebExceptionHandler;
import com.smartchargepark.user.controller.AuthController;
import com.smartchargepark.user.dto.CurrentUser;
import com.smartchargepark.user.dto.LoginResult;
import com.smartchargepark.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserCommonWebContractTest {
    private final AuthService auth = mock(AuthService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AuthController(auth)).setControllerAdvice(new CommonWebExceptionHandler()).build();

    @Test void currentUserErrorUsesSharedFieldsAndReal401() throws Exception {
        when(auth.current(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "private-marker"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.path").value("/api/auth/me"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test void loginAndCurrentUserKeepTheirSuccessfulDtos() throws Exception {
        when(auth.login(any())).thenReturn(new LoginResult("unit-marker", 2L, "演示用户", "DRIVER"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"driver2\",\"password\":\"unit-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").value("unit-marker"))
                .andExpect(jsonPath("$.userId").value(2)).andExpect(jsonPath("$.displayName").value("演示用户"))
                .andExpect(jsonPath("$.data").doesNotExist());
        when(auth.current("Bearer unit-marker")).thenReturn(new CurrentUser(2L, "演示用户", "DRIVER"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer unit-marker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.role").value("DRIVER")).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void invalidLoginBodyUsesShared400BeforeBusinessExecution() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
        verifyNoInteractions(auth);
    }
}
