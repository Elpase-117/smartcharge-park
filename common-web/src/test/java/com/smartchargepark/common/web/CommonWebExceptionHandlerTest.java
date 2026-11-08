package com.smartchargepark.common.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommonWebExceptionHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailureController())
            .setControllerAdvice(new CommonWebExceptionHandler()).build();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CommonWebExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    CommonWebExceptionHandlerTest() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach void detachLogger() { logger.detachAppender(appender); appender.stop(); }

    @Test
    void serializesOnlyTheFourPublicFields() throws Exception {
        String json = mapper.writeValueAsString(new ErrorResponse(401, "AUTH_REQUIRED", "请先登录", "/api/auth/me"));
        var node = mapper.readTree(json);
        ArrayList<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("status", "code", "message", "path");
        assertThat(node.get("status").asInt()).isEqualTo(401);
        assertThat(node.get("message").asText()).isEqualTo("请先登录");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 503})
    void preservesResponseStatusExceptionStatusWithoutExposingItsReason(int status) throws Exception {
        var result = mvc.perform(get("/rse/" + status + "?token=private-query-marker"))
                .andExpect(status().is(status)).andReturn();
        assertContract(result, status, "/rse/" + status);
        assertThat(appender.list).noneMatch(event -> event.getLevel().toString().equals("ERROR"));
    }

    @Test
    void businessExceptionUsesAnExplicitSafeBusinessCodeAndMessage() throws Exception {
        var result = mvc.perform(get("/business")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_FULL"))
                .andExpect(jsonPath("$.message").value("预约时段已满")).andReturn();
        assertContract(result, 409, "/business");
        assertThat(result.getResolvedException()).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requestBodyValidationIs400WithoutRejectedValues() throws Exception {
        var result = mvc.perform(post("/payload").contentType("application/json")
                        .content("{\"name\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertContract(result, 400, "/payload");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("quantity", "rejectedValue", "NotBlank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not-json", "{\"name\":\"private-marker\",\"quantity\":\"wrong-type\"}"})
    void malformedJsonAndJsonTypeMismatchAre400(String body) throws Exception {
        var result = mvc.perform(post("/payload").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andReturn();
        assertContract(result, 400, "/payload");
    }

    @Test
    void queryTypeMismatchIs400() throws Exception {
        var result = mvc.perform(get("/typed?amount=private-marker")).andExpect(status().isBadRequest()).andReturn();
        assertContract(result, 400, "/typed");
    }

    @Test
    void constraintViolationIs400() throws Exception {
        var result = mvc.perform(get("/constraint")).andExpect(status().isBadRequest()).andReturn();
        assertContract(result, 400, "/constraint");
    }

    @Test
    void bindExceptionIs400() throws Exception {
        var result = mvc.perform(get("/bind")).andExpect(status().isBadRequest()).andReturn();
        assertContract(result, 400, "/bind");
    }

    @Test
    void unknownErrorReturnsSafe500AndLogsSanitizedStackFrames() throws Exception {
        var result = mvc.perform(get("/unknown?token=private-query-marker"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误")).andReturn();
        assertContract(result, 500, "/unknown");
        assertThat(result.getResolvedException()).isInstanceOf(IllegalStateException.class);
        assertThat(appender.list).hasSize(1);
        var event = appender.list.get(0);
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getFormattedMessage()).contains("IllegalStateException", "path=/unknown")
                .doesNotContain("private-marker", "password", "SELECT");
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        assertThat(event.getThrowableProxy().getMessage()).doesNotContain("private-marker", "password", "SELECT");
        assertThat(event.getThrowableProxy().getCause()).isNull();
    }

    @Test
    void unsupportedMethodPreservesItsAllowHeader() throws Exception {
        var result = mvc.perform(post("/typed")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET")).andReturn();
        assertContract(result, 405, "/typed");
    }

    private void assertContract(MvcResult result, int status, String path) throws Exception {
        String content = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var body = mapper.readTree(content);
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asText()).isNotBlank();
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isEqualTo(path);
        assertThat(content).doesNotContain("private-marker", "private-query-marker", "SQLException", "IllegalStateException",
                "stackTrace", "exception", "SELECT", "password", "C:\\", "127.0.0.1", "token=");
    }

    @RestController
    static class FailureController {
        @GetMapping("/rse/{status}")
        void status(@PathVariable int status) {
            throw new ResponseStatusException(HttpStatus.valueOf(status), "SELECT password private-marker C:\\private\\app");
        }
        @GetMapping("/business")
        void business() { throw new BusinessException(HttpStatus.CONFLICT, "SLOT_FULL", "预约时段已满"); }
        @PostMapping("/payload")
        Object payload(@Valid @RequestBody Payload payload) { return payload; }
        @GetMapping("/typed")
        int typed(@RequestParam int amount) { return amount; }
        @GetMapping("/constraint")
        void constraint() { throw new ConstraintViolationException("private-marker", Set.of()); }
        @GetMapping("/bind")
        void bind() throws BindException {
            var errors = new BeanPropertyBindingResult(new Object(), "private-marker");
            errors.reject("private-code", "private-marker");
            throw new BindException(errors);
        }
        @GetMapping("/unknown")
        void unknown() { throw new IllegalStateException("SELECT password; Bearer private-marker; C:\\private\\app"); }
    }

    public record Payload(@NotBlank String name, @Positive int quantity) { }
}
