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
import xyz.playedu.api.controller.backend.AdminRoleController;
import xyz.playedu.api.controller.backend.AdminUserController;
import xyz.playedu.api.interceptor.AdminInterceptor;
import xyz.playedu.api.interceptor.ApiInterceptor;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.constant.BPermissionConstant;
import xyz.playedu.common.constant.BackendConstant;
import xyz.playedu.common.domain.AdminPermission;
import xyz.playedu.common.domain.AdminRole;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.service.AdminPermissionService;
import xyz.playedu.common.service.AdminRoleService;
import xyz.playedu.common.service.AdminUserService;
import xyz.playedu.common.service.AppConfigService;
import xyz.playedu.common.service.BackendAuthService;
import xyz.playedu.common.service.RateLimiterService;
import xyz.playedu.common.types.paginate.AdminUserPaginateFilter;
import xyz.playedu.common.types.paginate.PaginationResult;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/** Real HTTP coverage for administrator accounts, roles and permission enforcement. */
@SpringBootTest(
        classes = BackendAdminPermissionHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "playedu.core.testing=false",
            "playedu.limiter.duration=60",
            "playedu.limiter.limit=100"
        })
class BackendAdminPermissionHttpTest {
    private static final String ADMIN_USER = "/backend/v1/admin-user";
    private static final String ADMIN_ROLE = "/backend/v1/admin-role";
    private static final String TOKEN = "backend-admin-permission-token";

    @Configuration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({
        AdminUserController.class,
        AdminRoleController.class,
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
    @MockBean private AdminRoleService roleService;
    @MockBean private AdminPermissionService permissionService;
    @MockBean private BackendBus backendBus;
    @MockBean private BackendAuthService authService;
    @MockBean private AppConfigService appConfigService;
    @MockBean private RateLimiterService rateLimiterService;

    private final HashMap<String, Boolean> permissions = new HashMap<>();
    private AdminUser currentAdmin;
    private boolean authenticated;

    @BeforeEach
    void setUp() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        authenticated = true;
        permissions.clear();
        currentAdmin = adminUser(1, "Current Admin", "current@example.com");

        when(appConfigService.keyValues()).thenReturn(Map.of());
        when(rateLimiterService.current(anyString(), anyLong())).thenReturn(0L);
        when(authService.check()).thenAnswer(invocation -> authenticated);
        when(authService.userId()).thenReturn(currentAdmin.getId());
        when(adminUserService.findById(currentAdmin.getId())).thenReturn(currentAdmin);
        when(backendBus.adminUserPermissions(currentAdmin.getId()))
                .thenAnswer(invocation -> new HashMap<>(permissions));
    }

    @Test
    void allAdministratorAndRoleEndpointsRequireAuthentication() {
        authenticated = false;
        List<Request> requests =
                List.of(
                        new Request(HttpMethod.GET, ADMIN_USER + "/index", null),
                        new Request(HttpMethod.GET, ADMIN_USER + "/create", null),
                        new Request(HttpMethod.POST, ADMIN_USER + "/create", userPayload("secret")),
                        new Request(HttpMethod.GET, ADMIN_USER + "/2", null),
                        new Request(HttpMethod.PUT, ADMIN_USER + "/2", userPayload("")),
                        new Request(HttpMethod.DELETE, ADMIN_USER + "/2", null),
                        new Request(HttpMethod.GET, ADMIN_ROLE + "/index", null),
                        new Request(HttpMethod.GET, ADMIN_ROLE + "/create", null),
                        new Request(HttpMethod.POST, ADMIN_ROLE + "/create", rolePayload()),
                        new Request(HttpMethod.GET, ADMIN_ROLE + "/2", null),
                        new Request(HttpMethod.PUT, ADMIN_ROLE + "/2", rolePayload()),
                        new Request(HttpMethod.DELETE, ADMIN_ROLE + "/2", null));

        for (Request request : requests) {
            ResponseEntity<String> response =
                    exchange(request.method(), request.path(), request.body(), null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(read(response).get("msg").asText()).isEqualTo("请登录");
        }
    }

    @Test
    void administratorEndpointsRejectMissingPermissions() {
        assertForbidden(exchange(HttpMethod.GET, ADMIN_USER + "/index", null, TOKEN));
        assertForbidden(
                exchange(HttpMethod.POST, ADMIN_USER + "/create", userPayload("secret"), TOKEN));
        assertForbidden(exchange(HttpMethod.GET, ADMIN_ROLE + "/create", null, TOKEN));

        verify(adminUserService, never()).paginate(anyInt(), anyInt(), any());
        verify(adminUserService, never())
                .createWithRoleIds(anyString(), anyString(), anyString(), anyInt(), any());
        verify(permissionService, never()).listOrderBySortAsc();
    }

    @Test
    void administratorListAppliesFiltersAndReturnsRoleMappings() {
        grant(BPermissionConstant.ADMIN_USER_INDEX);
        AdminUser listedUser = adminUser(2, "Alice", "alice@example.com");
        PaginationResult<AdminUser> page = new PaginationResult<>();
        page.setData(List.of(listedUser));
        page.setTotal(1L);
        when(adminUserService.paginate(anyInt(), anyInt(), any())).thenReturn(page);
        when(adminUserService.getAdminUserRoleIds(List.of(2))).thenReturn(Map.of(2, List.of(3)));
        when(roleService.list()).thenReturn(List.of(role(3, "Auditor", "auditor")));

        JsonNode response = get(ADMIN_USER + "/index?page=2&size=5&name=Alice&role_id=3");

        assertThat(response.at("/data/total").asLong()).isEqualTo(1);
        assertThat(response.at("/data/data/0/id").asInt()).isEqualTo(2);
        assertThat(response.at("/data/user_role_ids/2/0").asInt()).isEqualTo(3);
        assertThat(response.at("/data/roles/3/0/name").asText()).isEqualTo("Auditor");
        ArgumentCaptor<AdminUserPaginateFilter> filter =
                ArgumentCaptor.forClass(AdminUserPaginateFilter.class);
        verify(adminUserService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getName()).isEqualTo("Alice");
        assertThat(filter.getValue().getRoleId()).isEqualTo(3);
    }

    @Test
    void administratorCreatePageReturnsAvailableRoles() {
        grant(BPermissionConstant.ADMIN_USER_CUD);
        when(roleService.list()).thenReturn(List.of(role(3, "Auditor", "auditor")));

        JsonNode response = get(ADMIN_USER + "/create");

        assertThat(response.at("/data/roles/0/id").asInt()).isEqualTo(3);
    }

    @Test
    void administratorCreationValidatesRequiredFieldsAndPassword() {
        grant(BPermissionConstant.ADMIN_USER_CUD);

        JsonNode invalid = post(ADMIN_USER + "/create", Map.of("name", "", "email", "bad"));
        JsonNode emptyPassword = post(ADMIN_USER + "/create", userPayload(""));

        assertThat(invalid.get("code").asInt()).isEqualTo(406);
        assertBusinessError(emptyPassword, "请输入密码");
        verify(adminUserService, never())
                .createWithRoleIds(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void administratorCanBeCreatedWithRoles() throws Exception {
        grant(BPermissionConstant.ADMIN_USER_CUD);

        JsonNode response = post(ADMIN_USER + "/create", userPayload("secret"));

        assertSuccess(response);
        verify(adminUserService)
                .createWithRoleIds(
                        "New Admin", "new@example.com", "secret", 0, new Integer[] {3, 4});
    }

    @Test
    void administratorEditReturnsAccountAndAssignedRoles() throws Exception {
        grant(BPermissionConstant.ADMIN_USER_CUD);
        AdminUser target = adminUser(2, "Alice", "alice@example.com");
        when(adminUserService.findOrFail(2)).thenReturn(target);
        when(adminUserService.getRoleIdsByUserId(2)).thenReturn(List.of(3, 4));

        JsonNode response = get(ADMIN_USER + "/2");

        assertThat(response.at("/data/user/name").asText()).isEqualTo("Alice");
        assertThat(response.at("/data/role_ids"))
                .containsExactly(json.valueToTree(3), json.valueToTree(4));
    }

    @Test
    void administratorCanBeUpdatedAndDeleted() throws Exception {
        grant(BPermissionConstant.ADMIN_USER_CUD);
        AdminUser target = adminUser(2, "Alice", "alice@example.com");
        when(adminUserService.findOrFail(2)).thenReturn(target);

        JsonNode updated = put(ADMIN_USER + "/2", userPayload(""));
        JsonNode deleted = delete(ADMIN_USER + "/2");

        assertSuccess(updated);
        assertSuccess(deleted);
        verify(adminUserService)
                .updateWithRoleIds(
                        target, "New Admin", "new@example.com", "", 0, new Integer[] {3, 4});
        verify(adminUserService).removeWithRoleIds(2);
    }

    @Test
    void roleListOnlyRequiresAuthentication() {
        when(roleService.list()).thenReturn(List.of(role(3, "Auditor", "auditor")));

        JsonNode response = get(ADMIN_ROLE + "/index");

        assertThat(response.at("/data/0/name").asText()).isEqualTo("Auditor");
        verify(backendBus, atLeastOnce()).adminUserPermissions(currentAdmin.getId());
    }

    @Test
    void roleCreatePageGroupsActionAndDataPermissions() {
        grant(BPermissionConstant.ADMIN_ROLE);
        when(permissionService.listOrderBySortAsc())
                .thenReturn(
                        List.of(
                                permission(11, BPermissionConstant.TYPE_ACTION),
                                permission(12, BPermissionConstant.TYPE_DATA)));

        JsonNode response = get(ADMIN_ROLE + "/create");

        assertThat(response.at("/data/perm_action/action/0/id").asInt()).isEqualTo(11);
        assertThat(response.at("/data/perm_action/data/0/id").asInt()).isEqualTo(12);
    }

    @Test
    void roleCreationValidatesInputAndPersistsPermissions() {
        grant(BPermissionConstant.ADMIN_ROLE);

        JsonNode invalid = post(ADMIN_ROLE + "/create", Map.of("name", ""));
        JsonNode created = post(ADMIN_ROLE + "/create", rolePayload());

        assertThat(invalid.get("code").asInt()).isEqualTo(406);
        assertSuccess(created);
        verify(roleService).createWithPermissionIds("Auditor", new Integer[] {11, 12});
    }

    @Test
    void roleEditSeparatesActionAndDataPermissionIds() throws Exception {
        grant(BPermissionConstant.ADMIN_ROLE);
        AdminRole target = role(3, "Auditor", "auditor");
        when(roleService.findOrFail(3)).thenReturn(target);
        when(roleService.getPermissionIdsByRoleId(3)).thenReturn(List.of(11, 12));
        when(permissionService.chunks(List.of(11, 12)))
                .thenReturn(
                        List.of(
                                permission(11, BPermissionConstant.TYPE_ACTION),
                                permission(12, BPermissionConstant.TYPE_DATA)));

        JsonNode response = get(ADMIN_ROLE + "/3");

        assertThat(response.at("/data/role/id").asInt()).isEqualTo(3);
        assertThat(response.at("/data/perm_action/0").asInt()).isEqualTo(11);
        assertThat(response.at("/data/perm_data/0").asInt()).isEqualTo(12);
    }

    @Test
    void superAdministratorRoleCannotBeUpdatedOrDeleted() throws Exception {
        grant(BPermissionConstant.ADMIN_ROLE);
        AdminRole superRole = role(1, "Super Admin", BackendConstant.SUPER_ADMIN_ROLE);
        when(roleService.findOrFail(1)).thenReturn(superRole);

        JsonNode updated = put(ADMIN_ROLE + "/1", rolePayload());
        JsonNode deleted = delete(ADMIN_ROLE + "/1");

        assertBusinessError(updated, "超级管理权限无法编辑");
        assertBusinessError(deleted, "超级管理角色无法删除");
        verify(roleService, never()).updateWithPermissionIds(any(), anyString(), any());
        verify(roleService, never()).removeWithPermissions(any());
    }

    @Test
    void ordinaryRoleCanBeUpdatedAndDeleted() throws Exception {
        grant(BPermissionConstant.ADMIN_ROLE);
        AdminRole target = role(3, "Auditor", "auditor");
        when(roleService.findOrFail(3)).thenReturn(target);

        JsonNode updated = put(ADMIN_ROLE + "/3", rolePayload());
        JsonNode deleted = delete(ADMIN_ROLE + "/3");

        assertSuccess(updated);
        assertSuccess(deleted);
        verify(roleService).updateWithPermissionIds(target, "Auditor", new Integer[] {11, 12});
        verify(roleService).removeWithPermissions(target);
    }

    private void grant(String permission) {
        permissions.put(permission, true);
    }

    private AdminUser adminUser(int id, String name, String email) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setIsBanLogin(0);
        return user;
    }

    private AdminRole role(int id, String name, String slug) {
        AdminRole role = new AdminRole();
        role.setId(id);
        role.setName(name);
        role.setSlug(slug);
        return role;
    }

    private AdminPermission permission(int id, String type) {
        AdminPermission permission = new AdminPermission();
        permission.setId(id);
        permission.setType(type);
        permission.setName(type + " permission");
        return permission;
    }

    private Map<String, Object> userPayload(String password) {
        return Map.of(
                "name",
                "New Admin",
                "email",
                "new@example.com",
                "password",
                password,
                "is_ban_login",
                0,
                "role_ids",
                List.of(3, 4));
    }

    private Map<String, Object> rolePayload() {
        return Map.of("name", "Auditor", "permission_ids", List.of(11, 12));
    }

    private JsonNode get(String path) {
        return read(exchange(HttpMethod.GET, path, null, TOKEN));
    }

    private JsonNode post(String path, Object body) {
        return read(exchange(HttpMethod.POST, path, body, TOKEN));
    }

    private JsonNode put(String path, Object body) {
        return read(exchange(HttpMethod.PUT, path, body, TOKEN));
    }

    private JsonNode delete(String path) {
        return read(exchange(HttpMethod.DELETE, path, null, TOKEN));
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
        assertThat(response.get("code").asInt()).isZero();
    }

    private void assertBusinessError(JsonNode response, String message) {
        assertThat(response.get("code").asInt()).isEqualTo(-1);
        assertThat(response.get("msg").asText()).isEqualTo(message);
    }

    private record Request(HttpMethod method, String path, Object body) {}
}
