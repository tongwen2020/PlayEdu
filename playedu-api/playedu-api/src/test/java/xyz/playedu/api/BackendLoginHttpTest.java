/*
 * Copyright (C) 2023 杭州白书科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.playedu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.playedu.api.controller.ExceptionController;
import xyz.playedu.api.controller.backend.LoginController;
import xyz.playedu.api.interceptor.AdminInterceptor;
import xyz.playedu.api.interceptor.ApiInterceptor;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.constant.BPermissionConstant;
import xyz.playedu.common.context.BCtx;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.service.AdminUserService;
import xyz.playedu.common.service.AppConfigService;
import xyz.playedu.common.service.BackendAuthService;
import xyz.playedu.common.service.RateLimiterService;
import xyz.playedu.common.util.HelperUtil;
import xyz.playedu.common.util.MemoryCacheUtil;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/** Real HTTP, validation, interceptors and permission aspect with mocked account services. */
@SpringBootTest(
        classes = BackendLoginHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "playedu.core.testing=false",
            "playedu.limiter.duration=60",
            "playedu.limiter.limit=100"
        })
class BackendLoginHttpTest {
    private static final String AUTH = "/backend/v1/auth";
    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "correct-password";
    private static final String SALT = "test-salt";
    private static final String TOKEN = "backend-test-token";
    private static final String LIMIT_KEY = "admin-login-limit:" + EMAIL;

    @Configuration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({
        LoginController.class,
        ExceptionController.class,
        AdminInterceptor.class,
        ApiInterceptor.class,
        BackendPermissionAspect.class,
        PlayEduConfig.class
    })
    static class Harness implements WebMvcConfigurer {
        @Autowired private ApiInterceptor apiInterceptor;
        @Autowired private AdminInterceptor adminInterceptor;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(apiInterceptor).addPathPatterns("/**");
            registry.addInterceptor(adminInterceptor).addPathPatterns("/backend/**");
        }
    }

    @Autowired private TestRestTemplate http;
    @Autowired private ObjectMapper json;
    @Autowired private PlayEduConfig playEduConfig;
    @MockBean private AdminUserService adminUserService;
    @MockBean private BackendBus backendBus;
    @MockBean private BackendAuthService authService;
    @MockBean private AppConfigService appConfigService;
    @MockBean private RateLimiterService rateLimiterService;

    private AdminUser loginAdmin;
    private AdminUser authenticatedAdmin;
    private long apiRequestCount;
    private long accountRequestCount;
    private boolean authenticated;
    private int authenticatedUserId;
    private HashMap<String, Boolean> permissions;

    @BeforeEach
    void setUp() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        BCtx.remove();
        MemoryCacheUtil.del(LIMIT_KEY);
        playEduConfig.setTesting(false);
        apiRequestCount = 0;
        accountRequestCount = 0;
        authenticated = false;
        authenticatedUserId = 1;
        permissions = new HashMap<>();

        loginAdmin = admin(1, 0);
        authenticatedAdmin = loginAdmin;

        when(appConfigService.keyValues()).thenReturn(Map.of());
        when(rateLimiterService.current(anyString(), anyLong()))
                .thenAnswer(
                        invocation ->
                                invocation.<String>getArgument(0).startsWith("admin-login-limit:")
                                        ? accountRequestCount
                                        : apiRequestCount);
        when(authService.check()).thenAnswer(invocation -> authenticated);
        when(authService.userId()).thenAnswer(invocation -> authenticatedUserId);
        when(adminUserService.findById(anyInt()))
                .thenAnswer(
                        invocation ->
                                Objects.equals(invocation.getArgument(0), authenticatedUserId)
                                        ? authenticatedAdmin
                                        : null);
        when(backendBus.adminUserPermissions(anyInt()))
                .thenAnswer(invocation -> new HashMap<>(permissions));
    }

    @AfterEach
    void tearDown() {
        BCtx.remove();
        MemoryCacheUtil.del(LIMIT_KEY);
    }

    @Test
    void loginRequiresEmailAndPassword() {
        JsonNode missingEmail = post(AUTH + "/login", Map.of("password", PASSWORD));
        JsonNode missingPassword = post(AUTH + "/login", Map.of("email", EMAIL));

        assertThat(missingEmail.get("code").asInt()).isEqualTo(406);
        assertThat(missingEmail.get("msg").asText()).contains("邮箱");
        assertThat(missingPassword.get("code").asInt()).isEqualTo(406);
        assertThat(missingPassword.get("msg").asText()).contains("密码");
        verifyNoInteractions(authService);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(adminUserService.findByEmail(EMAIL)).thenReturn(null);

        JsonNode response = login(PASSWORD);

        assertBusinessError(response, "邮箱或密码错误");
        verify(authService, never()).loginUsingId(anyInt(), anyString());
    }

    @Test
    void loginRejectsWrongPassword() {
        when(adminUserService.findByEmail(EMAIL)).thenReturn(loginAdmin);

        JsonNode response = login("wrong-password");

        assertBusinessError(response, "邮箱或密码错误");
        verify(authService, never()).loginUsingId(anyInt(), anyString());
    }

    @Test
    void loginRejectsBannedAdministrator() {
        loginAdmin.setIsBanLogin(1);
        when(adminUserService.findByEmail(EMAIL)).thenReturn(loginAdmin);

        JsonNode response = login(PASSWORD);

        assertBusinessError(response, "当前管理员已禁止登录");
        verify(authService, never()).loginUsingId(anyInt(), anyString());
    }

    @Test
    void loginLocksAccountAfterTooManyAttempts() {
        accountRequestCount = 11;
        MemoryCacheUtil.set(LIMIT_KEY, 11L, 120L);
        when(adminUserService.findByEmail(EMAIL)).thenReturn(loginAdmin);

        JsonNode response = login(PASSWORD);

        assertThat(response.get("code").asInt()).isEqualTo(-1);
        assertThat(response.get("msg").asText()).contains("账号已被锁定").contains("分钟");
        verify(authService, never()).loginUsingId(anyInt(), anyString());
    }

    @Test
    void testingModeBypassesAccountLock() {
        accountRequestCount = 11;
        playEduConfig.setTesting(true);
        successfulLoginMocks();

        JsonNode response = login(PASSWORD);

        assertThat(response.at("/data/token").asText()).isEqualTo(TOKEN);
        verify(authService).loginUsingId(eq(loginAdmin.getId()), contains(AUTH + "/login"));
    }

    @Test
    void successfulLoginReturnsTokenAndClearsAttemptCounter() {
        MemoryCacheUtil.set(LIMIT_KEY, 3L, 120L);
        successfulLoginMocks();

        JsonNode response = login(PASSWORD);

        assertThat(response.get("code").asInt()).isZero();
        assertThat(response.at("/data/token").asText()).isEqualTo(TOKEN);
        assertThat(MemoryCacheUtil.exists(LIMIT_KEY)).isFalse();
        verify(authService).loginUsingId(eq(loginAdmin.getId()), contains(AUTH + "/login"));
    }

    @Test
    void globalRateLimiterRejectsLoginBeforeCredentialChecks() {
        apiRequestCount = 100;

        ResponseEntity<String> response =
                exchange(
                        HttpMethod.POST,
                        AUTH + "/login",
                        Map.of("email", EMAIL, "password", PASSWORD),
                        null);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(read(response).get("msg").asText()).isEqualTo("太多请求");
        verify(adminUserService, never()).findByEmail(anyString());
    }

    @Test
    void protectedEndpointsRequireAuthentication() {
        ResponseEntity<String> detail = exchange(HttpMethod.GET, AUTH + "/detail", null, null);
        ResponseEntity<String> logout = exchange(HttpMethod.POST, AUTH + "/logout", Map.of(), null);

        assertThat(detail.getStatusCode().value()).isEqualTo(401);
        assertThat(read(detail).get("msg").asText()).isEqualTo("请登录");
        assertThat(logout.getStatusCode().value()).isEqualTo(401);
        assertThat(read(logout).get("msg").asText()).isEqualTo("请登录");
        verify(authService, never()).logout();
    }

    @Test
    void authenticatedAdministratorCanLogout() {
        authenticated = true;

        JsonNode response = read(exchange(HttpMethod.POST, AUTH + "/logout", Map.of(), TOKEN));

        assertThat(response.get("code").asInt()).isZero();
        assertThat(response.get("msg").asText()).isEqualTo("success");
        verify(authService).logout();
    }

    @Test
    void detailReturnsCurrentAdministratorAndPermissions() {
        authenticated = true;
        permissions.put(BPermissionConstant.PASSWORD_CHANGE, true);

        JsonNode response = read(exchange(HttpMethod.GET, AUTH + "/detail", null, TOKEN));

        assertThat(response.get("code").asInt()).isZero();
        assertThat(response.at("/data/user/id").asInt()).isEqualTo(loginAdmin.getId());
        assertThat(response.at("/data/user/name").asText()).isEqualTo(loginAdmin.getName());
        assertThat(
                        response.at("/data/permissions/" + BPermissionConstant.PASSWORD_CHANGE)
                                .asBoolean())
                .isTrue();
    }

    @Test
    void protectedEndpointRejectsMissingAndBannedAdministrators() {
        authenticated = true;
        authenticatedAdmin = null;
        ResponseEntity<String> missing = exchange(HttpMethod.GET, AUTH + "/detail", null, TOKEN);
        assertThat(missing.getStatusCode().value()).isEqualTo(401);
        assertThat(read(missing).get("msg").asText()).isEqualTo("管理员不存在");

        authenticatedAdmin = admin(1, 1);
        ResponseEntity<String> banned = exchange(HttpMethod.GET, AUTH + "/detail", null, TOKEN);
        assertThat(banned.getStatusCode().value()).isEqualTo(403);
        assertThat(read(banned).get("msg").asText()).isEqualTo("当前管理员禁止登录");
    }

    @Test
    void passwordChangeRequiresPermissionAndValidInput() {
        authenticated = true;
        JsonNode invalid =
                read(
                        exchange(
                                HttpMethod.PUT,
                                AUTH + "/password",
                                Map.of("old_password", PASSWORD),
                                TOKEN));
        assertThat(invalid.get("code").asInt()).isEqualTo(406);

        JsonNode forbidden =
                read(
                        exchange(
                                HttpMethod.PUT,
                                AUTH + "/password",
                                passwordPayload(PASSWORD, "new-password"),
                                TOKEN));
        assertThat(forbidden.get("code").asInt()).isEqualTo(403);
        assertThat(forbidden.get("msg").asText()).isEqualTo("权限不足");
        verify(adminUserService, never()).passwordChange(any(), anyString());
    }

    @Test
    void passwordChangeRejectsWrongOldPassword() {
        authenticated = true;
        permissions.put(BPermissionConstant.PASSWORD_CHANGE, true);

        JsonNode response =
                read(
                        exchange(
                                HttpMethod.PUT,
                                AUTH + "/password",
                                passwordPayload("wrong-password", "new-password"),
                                TOKEN));

        assertBusinessError(response, "原密码不正确");
        verify(adminUserService, never()).passwordChange(any(), anyString());
    }

    @Test
    void passwordChangeUpdatesPasswordWhenAuthorized() {
        authenticated = true;
        permissions.put(BPermissionConstant.PASSWORD_CHANGE, true);

        JsonNode response =
                read(
                        exchange(
                                HttpMethod.PUT,
                                AUTH + "/password",
                                passwordPayload(PASSWORD, "new-password"),
                                TOKEN));

        assertThat(response.get("code").asInt()).isZero();
        verify(adminUserService).passwordChange(loginAdmin, "new-password");
    }

    private void successfulLoginMocks() {
        when(adminUserService.findByEmail(EMAIL)).thenReturn(loginAdmin);
        when(authService.loginUsingId(eq(loginAdmin.getId()), anyString())).thenReturn(TOKEN);
    }

    private AdminUser admin(int id, int isBanLogin) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setName("Test Admin");
        user.setEmail(EMAIL);
        user.setSalt(SALT);
        user.setPassword(HelperUtil.MD5(PASSWORD + SALT).toLowerCase());
        user.setIsBanLogin(isBanLogin);
        user.setLoginTimes(2);
        return user;
    }

    private JsonNode login(String password) {
        return post(AUTH + "/login", Map.of("email", EMAIL, "password", password));
    }

    private JsonNode post(String path, Object body) {
        return read(exchange(HttpMethod.POST, path, body, null));
    }

    private Map<String, String> passwordPayload(String oldPassword, String newPassword) {
        return Map.of("old_password", oldPassword, "new_password", newPassword);
    }

    private ResponseEntity<String> exchange(
            HttpMethod method, String path, Object body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode read(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody());
        } catch (Exception e) {
            throw new AssertionError("Response is not valid JSON: " + response.getBody(), e);
        }
    }

    private void assertBusinessError(JsonNode response, String message) {
        assertThat(response.get("code").asInt()).isEqualTo(-1);
        assertThat(response.get("msg").asText()).isEqualTo(message);
    }
}
