package com.smartchargepark.user;

import com.smartchargepark.user.dto.*;
import com.smartchargepark.user.mapper.AppUserMapper;
import com.smartchargepark.user.service.AuthService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:users;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.cloud.discovery.enabled=false", "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false", "spring.cloud.nacos.discovery.health-indicator.enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false"
})
class UserServiceTest {
    @Autowired TestRestTemplate http;
    @Autowired AuthService auth;
    @Autowired AppUserMapper users;
    @Autowired Flyway flyway;

    @Test void startsContextAndHealthWithoutOtherBusinessDatabases() {
        assertThat(http.getForEntity("/actuator/health", Map.class).getBody()).containsEntry("status", "UP");
    }

    @Test void flywayBuildsEmptySchemaAndPersistsBothDemoUsers() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("1");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(users.selectCount(null)).isEqualTo(2);
        assertThat(users.selectById(1L).getUsername()).isEqualTo("driver");
        assertThat(users.selectById(2L).getUsername()).isEqualTo("driver2");
    }

    @Test void existingDriverLoginKeepsFrontendFields() {
        var response = http.postForEntity("/api/auth/login", new LoginRequest("driver", "123456"), LoginResult.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().userId()).isEqualTo(1L);
        assertThat(response.getBody().displayName()).isEqualTo("演示车主");
        assertThat(response.getBody().role()).isEqualTo("DRIVER");
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test void differentPersistedUsersGetDifferentIdentitiesAndTokens() {
        var a = auth.login(new LoginRequest("driver", "123456"));
        var b = auth.login(new LoginRequest("driver2", "123456"));
        assertThat(a.userId()).isNotEqualTo(b.userId());
        assertThat(a.token()).isNotEqualTo(b.token());
        assertThat(auth.current("Bearer " + a.token()).userId()).isEqualTo(a.userId());
        assertThat(auth.current("Bearer " + b.token()).userId()).isEqualTo(b.userId());
        assertThat(users.selectById(b.userId()).getAccessToken()).isEqualTo(b.token());
    }

    @Test void rejectsWrongPassword() {
        var response = http.postForEntity("/api/auth/login", new LoginRequest("driver", "wrong"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("status", 401).containsEntry("code", "AUTH_REQUIRED")
                .containsEntry("message", "请先登录或重新登录").containsEntry("path", "/api/auth/login");
    }

    @Test void rejectsUnknownAccountAndEmptyCredentials() {
        assertThat(http.postForEntity("/api/auth/login", new LoginRequest("missing", "wrong"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/api/auth/login", new LoginRequest("", ""), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void exposesOnlySafeCurrentUserFields() {
        var login = auth.login(new LoginRequest("driver2", "123456"));
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(login.token());
        var response = http.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("userId", 2).containsEntry("displayName", "演示车主二")
                .doesNotContainKeys("password", "accessToken", "token");
    }

    @Test void rejectsMissingInvalidAndSupersededTokens() {
        assertThat(http.getForEntity("/api/auth/me", Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders invalid = new HttpHeaders(); invalid.setBearerAuth("not-a-session");
        assertThat(http.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(invalid), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        var old = auth.login(new LoginRequest("driver", "123456"));
        auth.login(new LoginRequest("driver", "123456"));
        HttpHeaders previous = new HttpHeaders(); previous.setBearerAuth(old.token());
        assertThat(http.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(previous), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
