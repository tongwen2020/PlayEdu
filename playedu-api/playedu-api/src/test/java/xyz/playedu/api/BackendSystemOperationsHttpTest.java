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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import xyz.playedu.api.controller.backend.AdminLogController;
import xyz.playedu.api.controller.backend.AppConfigController;
import xyz.playedu.api.controller.backend.CacheController;
import xyz.playedu.api.controller.backend.DashboardController;
import xyz.playedu.api.controller.backend.SystemController;
import xyz.playedu.api.interceptor.AdminInterceptor;
import xyz.playedu.api.interceptor.ApiInterceptor;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.constant.BPermissionConstant;
import xyz.playedu.common.constant.ConfigConstant;
import xyz.playedu.common.constant.SystemConstant;
import xyz.playedu.common.domain.AdminLog;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.domain.AppConfig;
import xyz.playedu.common.domain.User;
import xyz.playedu.common.service.AdminLogService;
import xyz.playedu.common.service.AdminUserService;
import xyz.playedu.common.service.AppConfigService;
import xyz.playedu.common.service.BackendAuthService;
import xyz.playedu.common.service.CategoryService;
import xyz.playedu.common.service.DepartmentService;
import xyz.playedu.common.service.RateLimiterService;
import xyz.playedu.common.service.UserService;
import xyz.playedu.common.types.paginate.AdminLogPaginateFiler;
import xyz.playedu.common.types.paginate.PaginationResult;
import xyz.playedu.common.util.MemoryCacheUtil;
import xyz.playedu.course.domain.UserLearnDurationStats;
import xyz.playedu.course.service.CourseService;
import xyz.playedu.course.service.UserLearnDurationStatsService;
import xyz.playedu.resource.service.ResourceService;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/** Real HTTP business coverage for backend configuration, operations, and dashboard controllers. */
@SpringBootTest(
        classes = BackendSystemOperationsHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "playedu.core.testing=false",
            "playedu.limiter.duration=60",
            "playedu.limiter.limit=100"
        })
class BackendSystemOperationsHttpTest {
    private static final String TOKEN = "backend-system-token";

    @Configuration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({
        AppConfigController.class,
        SystemController.class,
        CacheController.class,
        AdminLogController.class,
        DashboardController.class,
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
    @MockBean private AdminUserService adminUserService;
    @MockBean private BackendAuthService authService;
    @MockBean private RateLimiterService rateLimiterService;
    @MockBean private BackendBus backendBus;
    @MockBean private AppConfigService appConfigService;
    @MockBean private ResourceService resourceService;
    @MockBean private DepartmentService departmentService;
    @MockBean private CategoryService categoryService;
    @MockBean private UserService userService;
    @MockBean private CourseService courseService;
    @MockBean private UserLearnDurationStatsService learnStatsService;
    @MockBean private AdminLogService adminLogService;
    @MockBean private MemoryCacheUtil memoryCacheUtil;

    private final HashMap<String, Boolean> permissions = new HashMap<>();
    private boolean authenticated;

    @BeforeEach
    void setUp() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        authenticated = true;
        permissions.clear();

        AdminUser admin = new AdminUser();
        admin.setId(1);
        admin.setName("System Admin");
        admin.setEmail("system-admin@example.com");
        admin.setIsBanLogin(0);

        when(appConfigService.keyValues()).thenReturn(systemConfig());
        when(rateLimiterService.current(anyString(), anyLong())).thenReturn(0L);
        when(authService.check()).thenAnswer(invocation -> authenticated);
        when(authService.userId()).thenReturn(1);
        when(adminUserService.findById(1)).thenReturn(admin);
        when(backendBus.adminUserPermissions(1))
                .thenAnswer(invocation -> new HashMap<>(permissions));
        when(backendBus.isSuperAdmin()).thenReturn(false);
        when(resourceService.chunksPreSignUrlByIds(anyList())).thenReturn(Map.of());
        when(departmentService.groupByParent()).thenReturn(Map.of());
        when(categoryService.groupByParent()).thenReturn(Map.of());
    }

    @Test
    void allSystemAndDashboardEndpointsRequireAuthentication() {
        authenticated = false;
        List<Request> requests =
                List.of(
                        request(HttpMethod.GET, "/backend/v1/app-config"),
                        new Request(
                                HttpMethod.PUT,
                                "/backend/v1/app-config",
                                Map.of("data", Map.of("system.name", "PlayEdu"))),
                        request(HttpMethod.GET, "/backend/v1/system/config"),
                        request(HttpMethod.DELETE, "/backend/v1/cache/clear?cache_key=demo"),
                        request(HttpMethod.DELETE, "/backend/v1/cache/clear/all"),
                        request(HttpMethod.GET, "/backend/v1/admin/log/index"),
                        request(HttpMethod.GET, "/backend/v1/admin/log/detail/8"),
                        request(HttpMethod.GET, "/backend/v1/dashboard/index"));

        for (Request request : requests) {
            ResponseEntity<String> response =
                    exchange(request.method(), request.path(), request.body(), null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(read(response).get("msg").asText()).isEqualTo("请登录");
        }
        assertThat(getRaw("/backend/v1/cache/list").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void configurationAndAdminLogEndpointsEnforcePermissions() {
        assertForbidden(getRaw("/backend/v1/app-config"));
        assertForbidden(
                exchange(
                        HttpMethod.PUT,
                        "/backend/v1/app-config",
                        Map.of("data", Map.of("system.name", "PlayEdu")),
                        TOKEN));
        assertForbidden(getRaw("/backend/v1/admin/log/index"));
        assertForbidden(getRaw("/backend/v1/admin/log/detail/8"));
    }

    @Test
    void appConfigMasksPrivateValuesAndReturnsImageUrls() {
        grant(BPermissionConstant.SYSTEM_CONFIG);
        AppConfig privateConfig = appConfig("s3.secret_key", "secret", 1);
        AppConfig publicConfig = appConfig(ConfigConstant.SYSTEM_NAME, "PlayEdu", 0);
        when(appConfigService.allShow()).thenReturn(List.of(privateConfig, publicConfig));
        when(appConfigService.getAllImageValue()).thenReturn(List.of(7));
        when(resourceService.chunksPreSignUrlByIds(List.of(7)))
                .thenReturn(Map.of(7, "https://example/logo"));

        JsonNode response = get("/backend/v1/app-config");

        assertThat(response.at("/data/app_config/0/key_value").asText())
                .isEqualTo(SystemConstant.CONFIG_MASK);
        assertThat(response.at("/data/app_config/1/key_value").asText()).isEqualTo("PlayEdu");
        assertThat(response.at("/data/resource_url/7").asText()).isEqualTo("https://example/logo");
    }

    @Test
    void appConfigSaveNormalizesS3EndpointAndSkipsMaskedSecrets() {
        grant(BPermissionConstant.SYSTEM_CONFIG);
        Map<String, Object> body =
                Map.of(
                        "data",
                        Map.of(
                                ConfigConstant.S3_ENDPOINT,
                                "bucket.s3.example.com/",
                                ConfigConstant.S3_BUCKET,
                                "bucket",
                                ConfigConstant.SYSTEM_NAME,
                                "PlayEdu",
                                "s3.secret_key",
                                SystemConstant.CONFIG_MASK));

        assertSuccess(put("/backend/v1/app-config", body));

        ArgumentCaptor<HashMap<String, String>> saved = ArgumentCaptor.forClass(HashMap.class);
        verify(appConfigService).saveFromMap(saved.capture());
        assertThat(saved.getValue().get(ConfigConstant.S3_ENDPOINT))
                .isEqualTo("https://s3.example.com");
        assertThat(saved.getValue()).doesNotContainKey("s3.secret_key");
        assertThat(saved.getValue().get(ConfigConstant.SYSTEM_NAME)).isEqualTo("PlayEdu");
    }

    @Test
    void systemConfigReturnsBrandingAvatarAndLookupData() {
        when(appConfigService.getAllImageValue()).thenReturn(List.of(7));
        when(resourceService.chunksPreSignUrlByIds(List.of(7)))
                .thenReturn(Map.of(7, "https://example/logo"));

        JsonNode response = get("/backend/v1/system/config");

        assertThat(response.at("/data/system.name").asText()).isEqualTo("PlayEdu");
        assertThat(response.at("/data/member.default_avatar").asInt()).isEqualTo(9);
        assertThat(response.at("/data/ldap-enabled").asBoolean()).isFalse();
        assertThat(response.at("/data/departments").isObject()).isTrue();
        assertThat(response.at("/data/resource_categories").isObject()).isTrue();
    }

    @Test
    void cacheListAndClearOperationsAreCovered() {
        when(memoryCacheUtil.getAllKeys()).thenReturn(List.of("playedu:demo"), List.of());
        when(memoryCacheUtil.getAllCache())
                .thenReturn(Map.of("playedu:demo", Map.of("value", "cached")));

        JsonNode list = get("/backend/v1/cache/list");
        assertThat(list.at("/data/keys/0").asText()).isEqualTo("playedu:demo");

        MemoryCacheUtil.set("controller-test", "cached");
        assertSuccess(delete("/backend/v1/cache/clear?cache_key=controller-test"));
        assertThat(MemoryCacheUtil.get("controller-test")).isNull();
        assertSuccess(delete("/backend/v1/cache/clear/all"));
    }

    @Test
    void adminLogListUsesOwnerAndSearchFilters() {
        grant(BPermissionConstant.ADMIN_LOG);
        AdminLog log = new AdminLog();
        log.setId(8L);
        when(adminLogService.paginate(anyInt(), anyInt(), any())).thenReturn(page(List.of(log)));

        JsonNode response =
                get(
                        "/backend/v1/admin/log/index?page=2&size=5&admin_id=9&admin_name=Alice&module=课程&title=更新&opt=2&start_time=2026-09-01&end_time=2026-09-08");

        assertThat(response.at("/data/total").asLong()).isEqualTo(1);
        ArgumentCaptor<AdminLogPaginateFiler> filter =
                ArgumentCaptor.forClass(AdminLogPaginateFiler.class);
        verify(adminLogService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getAdminId()).isEqualTo(1);
        assertThat(filter.getValue().getAdminName()).isEqualTo("Alice");
        assertThat(filter.getValue().getModule()).isEqualTo("课程");
        assertThat(filter.getValue().getOpt()).isEqualTo(2);
    }

    @Test
    void adminLogDetailProtectsOwnershipAndReturnsExistingLog() {
        grant(BPermissionConstant.ADMIN_LOG);
        when(adminLogService.find(8, 1)).thenReturn(null);
        assertBusinessError(get("/backend/v1/admin/log/detail/8"), "日志不存在");

        AdminLog log = new AdminLog();
        log.setId(8L);
        when(adminLogService.find(8, 1)).thenReturn(log);
        assertThat(get("/backend/v1/admin/log/detail/8").at("/data/id").asInt()).isEqualTo(8);
    }

    @Test
    void superAdminCanFilterAndReadAnyAdminLog() {
        grant(BPermissionConstant.ADMIN_LOG);
        when(backendBus.isSuperAdmin()).thenReturn(true);
        when(adminLogService.paginate(anyInt(), anyInt(), any())).thenReturn(page(List.of()));
        AdminLog log = new AdminLog();
        log.setId(8L);
        when(adminLogService.find(8, 0)).thenReturn(log);

        get("/backend/v1/admin/log/index?admin_id=9");
        ArgumentCaptor<AdminLogPaginateFiler> filter =
                ArgumentCaptor.forClass(AdminLogPaginateFiler.class);
        verify(adminLogService).paginate(anyInt(), anyInt(), filter.capture());
        assertThat(filter.getValue().getAdminId()).isEqualTo(9);
        assertThat(get("/backend/v1/admin/log/detail/8").at("/data/id").asInt()).isEqualTo(8);
    }

    @Test
    void dashboardAggregatesCountsAndLearningLeaderboard() {
        when(userService.total()).thenReturn(100L);
        when(userService.todayCount()).thenReturn(5L);
        when(userService.yesterdayCount()).thenReturn(4L);
        when(courseService.total()).thenReturn(12L);
        when(departmentService.total()).thenReturn(8L);
        when(categoryService.total()).thenReturn(6L);
        when(adminUserService.total()).thenReturn(3L);
        when(resourceService.total(anyString())).thenReturn(10);
        when(resourceService.total(anyList())).thenReturn(20);
        when(learnStatsService.todayTotal()).thenReturn(3600L);
        when(learnStatsService.yesterdayTotal()).thenReturn(1800L);
        UserLearnDurationStats stats = new UserLearnDurationStats();
        stats.setUserId(7);
        stats.setDuration(900L);
        when(learnStatsService.top10()).thenReturn(List.of(stats));
        User user = new User();
        user.setId(7);
        user.setName("Alice");
        when(userService.chunks(eq(List.of(7)), anyList())).thenReturn(List.of(user));

        JsonNode response = get("/backend/v1/dashboard/index");

        assertThat(response.at("/data/user_total").asLong()).isEqualTo(100);
        assertThat(response.at("/data/course_total").asLong()).isEqualTo(12);
        assertThat(response.at("/data/resource_file_total").asInt()).isEqualTo(20);
        assertThat(response.at("/data/user_learn_top10_users/7/name").asText()).isEqualTo("A****");
    }

    private void grant(String permission) {
        permissions.put(permission, true);
    }

    private AppConfig appConfig(String key, String value, int isPrivate) {
        AppConfig config = new AppConfig();
        config.setKeyName(key);
        config.setKeyValue(value);
        config.setIsPrivate(isPrivate);
        config.setIsHidden(0);
        return config;
    }

    private Map<String, String> systemConfig() {
        return Map.ofEntries(
                Map.entry(ConfigConstant.SYSTEM_NAME, "PlayEdu"),
                Map.entry(ConfigConstant.SYSTEM_LOGO, "7"),
                Map.entry(ConfigConstant.SYSTEM_PC_URL, "https://pc.example.com"),
                Map.entry(ConfigConstant.SYSTEM_H5_URL, "https://h5.example.com"),
                Map.entry(ConfigConstant.MEMBER_DEFAULT_AVATAR, "9"),
                Map.entry(ConfigConstant.LDAP_ENABLED, "0"));
    }

    private <T> PaginationResult<T> page(List<T> data) {
        PaginationResult<T> page = new PaginationResult<>();
        page.setData(data);
        page.setTotal((long) data.size());
        return page;
    }

    private Request request(HttpMethod method, String path) {
        return new Request(method, path, null);
    }

    private JsonNode get(String path) {
        return read(getRaw(path));
    }

    private JsonNode put(String path, Object body) {
        return read(exchange(HttpMethod.PUT, path, body, TOKEN));
    }

    private JsonNode delete(String path) {
        return read(exchange(HttpMethod.DELETE, path, null, TOKEN));
    }

    private ResponseEntity<String> getRaw(String path) {
        return exchange(HttpMethod.GET, path, null, TOKEN);
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

    private void assertForbidden(ResponseEntity<String> response) {
        JsonNode body = read(response);
        assertThat(body.get("code").asInt()).isEqualTo(403);
        assertThat(body.get("msg").asText()).isEqualTo("权限不足");
    }

    private void assertSuccess(JsonNode response) {
        assertThat(response.get("code").asInt()).isEqualTo(0);
    }

    private void assertBusinessError(JsonNode response, String message) {
        assertThat(response.get("code").asInt()).isNotEqualTo(0);
        assertThat(response.get("msg").asText()).contains(message);
    }

    private record Request(HttpMethod method, String path, Object body) {}
}
