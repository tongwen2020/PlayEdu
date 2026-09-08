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
import java.util.ArrayList;
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
import xyz.playedu.api.controller.backend.DepartmentController;
import xyz.playedu.api.controller.backend.UserController;
import xyz.playedu.api.interceptor.AdminInterceptor;
import xyz.playedu.api.interceptor.ApiInterceptor;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.bus.LDAPBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.constant.BPermissionConstant;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.domain.Department;
import xyz.playedu.common.domain.User;
import xyz.playedu.common.service.AdminUserService;
import xyz.playedu.common.service.AppConfigService;
import xyz.playedu.common.service.BackendAuthService;
import xyz.playedu.common.service.DepartmentService;
import xyz.playedu.common.service.RateLimiterService;
import xyz.playedu.common.service.UserDepartmentService;
import xyz.playedu.common.service.UserService;
import xyz.playedu.common.types.paginate.PaginationResult;
import xyz.playedu.common.types.paginate.UserPaginateFilter;
import xyz.playedu.course.domain.Course;
import xyz.playedu.course.domain.CourseHour;
import xyz.playedu.course.domain.UserCourseHourRecord;
import xyz.playedu.course.domain.UserCourseRecord;
import xyz.playedu.course.service.CourseDepartmentUserService;
import xyz.playedu.course.service.CourseHourService;
import xyz.playedu.course.service.CourseService;
import xyz.playedu.course.service.UserCourseHourRecordService;
import xyz.playedu.course.service.UserCourseRecordService;
import xyz.playedu.course.service.UserLearnDurationStatsService;
import xyz.playedu.resource.service.ResourceService;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/** Real HTTP business coverage for users, departments and learning records. */
@SpringBootTest(
        classes = BackendUserDepartmentHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "playedu.core.testing=false",
            "playedu.limiter.duration=60",
            "playedu.limiter.limit=100"
        })
class BackendUserDepartmentHttpTest {
    private static final String USER = "/backend/v1/user";
    private static final String DEPARTMENT = "/backend/v1/department";
    private static final String TOKEN = "backend-user-department-token";

    @Configuration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({
        UserController.class,
        DepartmentController.class,
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
    @MockBean private UserService userService;
    @MockBean private UserDepartmentService userDepartmentService;
    @MockBean private DepartmentService departmentService;
    @MockBean private UserCourseHourRecordService userCourseHourRecordService;
    @MockBean private UserCourseRecordService userCourseRecordService;
    @MockBean private CourseHourService courseHourService;
    @MockBean private CourseService courseService;
    @MockBean private UserLearnDurationStatsService userLearnDurationStatsService;
    @MockBean private ResourceService resourceService;
    @MockBean private CourseDepartmentUserService courseDepartmentUserService;
    @MockBean private LDAPBus ldapBus;
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
        permissions.put(BPermissionConstant.DATA_USER_NAME, true);
        permissions.put(BPermissionConstant.DATA_USER_EMAIL, true);
        currentAdmin = new AdminUser();
        currentAdmin.setId(1);
        currentAdmin.setName("Current Admin");
        currentAdmin.setEmail("admin@example.com");
        currentAdmin.setIsBanLogin(0);

        when(appConfigService.keyValues()).thenReturn(Map.of());
        when(rateLimiterService.current(anyString(), anyLong())).thenReturn(0L);
        when(authService.check()).thenAnswer(invocation -> authenticated);
        when(authService.userId()).thenReturn(1);
        when(adminUserService.findById(1)).thenReturn(currentAdmin);
        when(backendBus.adminUserPermissions(1))
                .thenAnswer(invocation -> new HashMap<>(permissions));

        when(resourceService.chunksPreSignUrlByIds(anyList())).thenReturn(Map.of());
        when(userService.getDepIdsGroup(anyList())).thenReturn(Map.of());
        when(userService.getDepIdsByUserId(anyInt())).thenReturn(List.of());
        when(userService.existsEmailsByEmails(anyList())).thenReturn(List.of());
        when(departmentService.id2name()).thenReturn(Map.of());
        when(departmentService.getDepartmentsUserCount()).thenReturn(Map.of());
        when(departmentService.groupByParent()).thenReturn(Map.of());
        when(departmentService.all()).thenReturn(List.of());
        when(departmentService.listByParentId(anyInt())).thenReturn(List.of());
        when(courseService.chunks(anyList())).thenReturn(List.of());
        when(courseService.getOpenCoursesAndShow(anyInt())).thenReturn(new ArrayList<>());
        when(courseService.getDepCoursesAndShow(anyList())).thenReturn(new ArrayList<>());
        when(userCourseHourRecordService.getRecords(anyInt(), anyInt())).thenReturn(List.of());
        when(userCourseHourRecordService.getUserPerCourseEarliestRecord(anyInt()))
                .thenReturn(List.of());
        when(userLearnDurationStatsService.dateBetween(anyInt(), anyString(), anyString()))
                .thenReturn(List.of());
        when(userDepartmentService.getUserIdsByDepIds(anyList())).thenReturn(List.of());
        when(courseDepartmentUserService.getCourseIdsByDepId(anyInt())).thenReturn(List.of());
        when(departmentService.getUserIdsByDepId(anyInt())).thenReturn(List.of());
    }

    @Test
    void allUserAndDepartmentEndpointsRequireAuthentication() {
        authenticated = false;
        List<Request> requests =
                List.of(
                        request(HttpMethod.GET, USER + "/index"),
                        request(HttpMethod.GET, USER + "/create"),
                        new Request(HttpMethod.POST, USER + "/create", userPayload("secret")),
                        request(HttpMethod.GET, USER + "/2"),
                        new Request(HttpMethod.PUT, USER + "/2", userPayload("")),
                        request(HttpMethod.DELETE, USER + "/2"),
                        new Request(HttpMethod.POST, USER + "/store-batch", importPayload()),
                        request(HttpMethod.GET, USER + "/2/learn-hours"),
                        request(HttpMethod.GET, USER + "/2/learn-courses"),
                        request(HttpMethod.GET, USER + "/2/all-courses"),
                        request(HttpMethod.GET, USER + "/2/learn-course/8"),
                        request(HttpMethod.GET, USER + "/2/learn-stats"),
                        request(HttpMethod.DELETE, USER + "/2/learn-course/8"),
                        request(HttpMethod.DELETE, USER + "/2/learn-course/8/hour/9"),
                        request(HttpMethod.GET, DEPARTMENT + "/index"),
                        request(HttpMethod.GET, DEPARTMENT + "/departments"),
                        request(HttpMethod.GET, DEPARTMENT + "/create"),
                        new Request(HttpMethod.POST, DEPARTMENT + "/create", departmentPayload()),
                        request(HttpMethod.GET, DEPARTMENT + "/2"),
                        new Request(HttpMethod.PUT, DEPARTMENT + "/2", departmentPayload()),
                        request(HttpMethod.GET, DEPARTMENT + "/2/destroy"),
                        request(HttpMethod.DELETE, DEPARTMENT + "/2"),
                        new Request(
                                HttpMethod.PUT,
                                DEPARTMENT + "/update/sort",
                                Map.of("ids", List.of(2, 3))),
                        new Request(HttpMethod.PUT, DEPARTMENT + "/update/parent", parentPayload()),
                        request(HttpMethod.GET, DEPARTMENT + "/2/users"),
                        new Request(HttpMethod.POST, DEPARTMENT + "/ldap-sync", Map.of()));

        for (Request request : requests) {
            ResponseEntity<String> response =
                    exchange(request.method(), request.path(), request.body(), null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(read(response).get("msg").asText()).isEqualTo("请登录");
        }
    }

    @Test
    void eachUserPermissionGroupIsEnforced() {
        assertForbidden(getRaw(USER + "/index"));
        assertForbidden(postRaw(USER + "/create", userPayload("secret")));
        assertForbidden(putRaw(USER + "/2", userPayload("")));
        assertForbidden(deleteRaw(USER + "/2"));
        assertForbidden(getRaw(USER + "/2/learn-hours"));
        assertForbidden(deleteRaw(USER + "/2/learn-course/8"));
    }

    @Test
    void userIndexAppliesFiltersAndReturnsSupportingData() {
        grant(BPermissionConstant.USER_INDEX);
        User listed = user(2, "Alice", "alice@example.com", 7);
        PaginationResult<User> page = page(List.of(listed));
        when(userService.paginate(anyInt(), anyInt(), any())).thenReturn(page);
        when(userService.getDepIdsGroup(List.of(2))).thenReturn(Map.of(2, List.of(3)));
        when(departmentService.id2name()).thenReturn(Map.of(3, "研发部"));
        when(userService.total()).thenReturn(20L);
        when(departmentService.getDepartmentsUserCount()).thenReturn(Map.of(3, 5));
        when(resourceService.chunksPreSignUrlByIds(List.of(7)))
                .thenReturn(Map.of(7, "https://example/avatar"));

        JsonNode response =
                get(
                        USER
                                + "/index?page=2&size=5&name=Alice&dep_ids=0&created_at=2026-01-01,2026-01-31");

        assertThat(response.at("/data/total").asLong()).isEqualTo(1);
        assertThat(response.at("/data/pure_total").asLong()).isEqualTo(20);
        assertThat(response.at("/data/user_dep_ids/2/0").asInt()).isEqualTo(3);
        ArgumentCaptor<UserPaginateFilter> filter =
                ArgumentCaptor.forClass(UserPaginateFilter.class);
        verify(userService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getName()).isEqualTo("Alice");
        assertThat(filter.getValue().getDepIds()).isEmpty();
        assertThat(filter.getValue().getCreatedAt()).containsExactly("2026-01-01", "2026-01-31");
    }

    @Test
    void userCreationCoversValidationDuplicatePasswordAndSuccess() {
        grant(BPermissionConstant.USER_STORE);
        assertThat(get(USER + "/create").get("code").asInt()).isZero();

        JsonNode invalid = post(USER + "/create", Map.of("email", "bad"));
        assertThat(invalid.get("code").asInt()).isEqualTo(406);

        when(userService.emailIsExists("new@example.com")).thenReturn(true);
        assertBusinessError(post(USER + "/create", userPayload("secret")), "邮箱已存在");

        when(userService.emailIsExists("new@example.com")).thenReturn(false);
        assertBusinessError(post(USER + "/create", userPayload("")), "请输入密码");

        JsonNode created = post(USER + "/create", userPayload("secret"));
        assertSuccess(created);
        verify(userService)
                .createWithDepIds(
                        "new@example.com", "New User", 7, "secret", "ID-1", new Integer[] {3});
    }

    @Test
    void userCanBeReadUpdatedAndDeleted() throws Exception {
        grant(BPermissionConstant.USER_UPDATE);
        grant(BPermissionConstant.USER_DESTROY);
        User target = user(2, "Alice", "alice@example.com", 7);
        when(userService.findOrFail(2)).thenReturn(target);
        when(userService.getDepIdsByUserId(2)).thenReturn(List.of(3));

        JsonNode detail = get(USER + "/2");
        assertThat(detail.at("/data/user/name").asText()).isEqualTo("Alice");
        assertThat(detail.at("/data/dep_ids/0").asInt()).isEqualTo(3);

        Map<String, Object> changed = new HashMap<>(userPayload(""));
        changed.put("email", "changed@example.com");
        when(userService.emailIsExists("changed@example.com")).thenReturn(true);
        assertBusinessError(put(USER + "/2", changed), "邮箱已存在");

        when(userService.emailIsExists("changed@example.com")).thenReturn(false);
        assertSuccess(put(USER + "/2", changed));
        verify(userService)
                .updateWithDepIds(
                        target,
                        "changed@example.com",
                        "New User",
                        7,
                        "",
                        "ID-1",
                        new Integer[] {3});

        assertSuccess(delete(USER + "/2"));
        verify(userService).removeById(2);
    }

    @Test
    void batchImportRejectsEmptyAndInvalidRowsThenSavesValidRows() {
        grant(BPermissionConstant.USER_STORE);
        assertBusinessError(
                post(USER + "/store-batch", Map.of("users", List.of(), "start_line", 2)), "数据为空");

        Map<String, Object> invalidRow =
                Map.of(
                        "users",
                        List.of(
                                Map.of(
                                        "email", "",
                                        "name", "",
                                        "password", "",
                                        "deps", "不存在")),
                        "start_line",
                        2);
        JsonNode invalid = post(USER + "/store-batch", invalidRow);
        assertBusinessError(invalid, "导入数据有误");
        assertThat(invalid.at("/data/1/0").asText()).isEqualTo("第2行");

        Department department = department(3, "研发部", null);
        when(departmentService.all()).thenReturn(List.of(department));
        assertSuccess(post(USER + "/store-batch", importPayload()));
        verify(userService).saveBatch(anyList());
        verify(userDepartmentService).saveBatch(anyList());
    }

    @Test
    void learningListsAndCourseDetailReturnRelatedRecords() {
        grant(BPermissionConstant.USER_LEARN);
        UserCourseHourRecord hourRecord = new UserCourseHourRecord();
        hourRecord.setId(21);
        hourRecord.setHourId(9);
        CourseHour hour = new CourseHour();
        hour.setId(9);
        when(userCourseHourRecordService.paginate(anyInt(), anyInt(), any()))
                .thenReturn(page(List.of(hourRecord)));
        when(courseHourService.chunk(List.of(9))).thenReturn(List.of(hour));

        JsonNode hours = get(USER + "/2/learn-hours?page=1&size=5&is_finished=1");
        assertThat(hours.at("/data/hours/9/id").asInt()).isEqualTo(9);

        UserCourseRecord courseRecord = new UserCourseRecord();
        courseRecord.setId(31);
        courseRecord.setCourseId(8);
        Course course = course(8, "安全课", 17);
        when(userCourseRecordService.paginate(anyInt(), anyInt(), any()))
                .thenReturn(page(List.of(courseRecord)));
        when(courseService.chunks(List.of(8))).thenReturn(List.of(course));
        JsonNode courses = get(USER + "/2/learn-courses");
        assertThat(courses.at("/data/courses/8/title").asText()).isEqualTo("安全课");

        when(courseHourService.getHoursByCourseId(8)).thenReturn(List.of(hour));
        when(userCourseHourRecordService.getRecords(2, 8)).thenReturn(List.of(hourRecord));
        JsonNode detail = get(USER + "/2/learn-course/8");
        assertThat(detail.at("/data/learn_records/9/id").asInt()).isEqualTo(21);
    }

    @Test
    void allCoursesAndLearningStatsHandleEmptyHistory() {
        grant(BPermissionConstant.USER_LEARN);

        JsonNode allCourses = get(USER + "/2/all-courses");
        assertThat(allCourses.at("/data/open_courses").isArray()).isTrue();
        assertThat(allCourses.at("/data/user_course_records").isObject()).isTrue();

        JsonNode stats = get(USER + "/2/learn-stats");
        assertThat(stats.at("/data").size()).isBetween(30, 31);
        assertThat(stats.at("/data/0/value").asLong()).isZero();
    }

    @Test
    void learningRecordsCanBeDeleted() {
        grant(BPermissionConstant.USER_LEARN_DESTROY);

        assertSuccess(delete(USER + "/2/learn-course/8"));
        assertSuccess(delete(USER + "/2/learn-course/8/hour/9"));

        verify(userCourseRecordService).destroy(2, 8);
        verify(userCourseHourRecordService).remove(2, 8, 9);
    }

    @Test
    void departmentReadEndpointsReturnHierarchyCountsAndChildren() {
        Department root = department(2, "研发部", null);
        Department child = department(3, "平台组", "2");
        when(departmentService.groupByParent()).thenReturn(Map.of(0, List.of(root)));
        when(departmentService.all()).thenReturn(List.of(root));
        when(departmentService.getChildDepartmentsByParentChain(2, "2")).thenReturn(List.of(child));
        when(userDepartmentService.getUserIdsByDepIds(List.of(2, 3)))
                .thenReturn(List.of(10, 10, 11));
        when(userService.total()).thenReturn(8L);

        JsonNode index = get(DEPARTMENT + "/index");
        assertThat(index.at("/data/dep_user_count/2").asInt()).isEqualTo(2);
        assertThat(index.at("/data/user_total").asLong()).isEqualTo(8);

        when(departmentService.listByParentId(2)).thenReturn(List.of(child));
        JsonNode children = get(DEPARTMENT + "/departments?parent_id=2");
        assertThat(children.at("/data/0/name").asText()).isEqualTo("平台组");
    }

    @Test
    void departmentPermissionAndValidationAreEnforced() {
        assertForbidden(getRaw(DEPARTMENT + "/create"));
        assertForbidden(getRaw(DEPARTMENT + "/2/users"));

        grant(BPermissionConstant.DEPARTMENT_CUD);
        JsonNode invalid = post(DEPARTMENT + "/create", Map.of("name", ""));
        assertThat(invalid.get("code").asInt()).isEqualTo(406);
        JsonNode invalidParent = put(DEPARTMENT + "/update/parent", Map.of("id", 2));
        assertThat(invalidParent.get("code").asInt()).isEqualTo(406);
    }

    @Test
    void ldapModeBlocksDepartmentMutations() {
        grant(BPermissionConstant.DEPARTMENT_CUD);
        when(ldapBus.enabledLDAP()).thenReturn(true);

        assertBusinessError(post(DEPARTMENT + "/create", departmentPayload()), "已启用LDAP服务，禁止添加部门");
        assertBusinessError(put(DEPARTMENT + "/2", departmentPayload()), "已启用LDAP服务，禁止添加部门");
        assertBusinessError(get(DEPARTMENT + "/2/destroy"), "已启用LDAP服务，禁止添加部门");
        assertBusinessError(delete(DEPARTMENT + "/2"), "已启用LDAP服务，禁止添加部门");
        assertBusinessError(
                put(DEPARTMENT + "/update/parent", parentPayload()), "已启用LDAP服务，禁止添加部门");
    }

    @Test
    void departmentCanBeCreatedReadUpdatedSortedMovedAndDeleted() throws Exception {
        grant(BPermissionConstant.DEPARTMENT_CUD);
        Department target = department(2, "研发部", null);
        when(departmentService.findOrFail(2)).thenReturn(target);

        assertThat(get(DEPARTMENT + "/create").at("/data/departments").isObject()).isTrue();
        assertSuccess(post(DEPARTMENT + "/create", departmentPayload()));
        verify(departmentService).create("研发部", 0, 10);

        assertThat(get(DEPARTMENT + "/2").at("/data/name").asText()).isEqualTo("研发部");
        assertSuccess(put(DEPARTMENT + "/2", departmentPayload()));
        verify(departmentService).update(target, "研发部", 0, 10);

        assertSuccess(put(DEPARTMENT + "/update/sort", Map.of("ids", List.of(2, 3))));
        verify(departmentService).resetSort(List.of(2, 3));

        assertSuccess(put(DEPARTMENT + "/update/parent", parentPayload()));
        verify(departmentService).changeParent(2, 0, List.of(2, 3));

        assertSuccess(delete(DEPARTMENT + "/2"));
        verify(departmentService).destroy(2);
    }

    @Test
    void departmentPreDeleteReturnsAffectedChildrenCoursesAndUsers() {
        grant(BPermissionConstant.DEPARTMENT_CUD);
        when(courseDepartmentUserService.getCourseIdsByDepId(2)).thenReturn(List.of(8));
        when(departmentService.getUserIdsByDepId(2)).thenReturn(List.of(10));
        when(departmentService.listByParentId(2)).thenReturn(List.of(department(3, "平台组", "2")));
        when(courseService.chunks(eq(List.of(8)), anyList()))
                .thenReturn(List.of(course(8, "安全课", 17)));
        when(userService.chunks(eq(List.of(10)), anyList()))
                .thenReturn(List.of(user(10, "Alice", "alice@example.com", 7)));

        JsonNode response = get(DEPARTMENT + "/2/destroy");

        assertThat(response.at("/data/courses/0/id").asInt()).isEqualTo(8);
        assertThat(response.at("/data/users/0/id").asInt()).isEqualTo(10);
        assertThat(response.at("/data/children/0/id").asInt()).isEqualTo(3);
    }

    @Test
    void departmentUsersReturnSelectedCoursesAndLearningProgress() throws Exception {
        grant(BPermissionConstant.DEPARTMENT_USER_LEARN);
        Department target = department(2, "研发部", "1");
        when(departmentService.findOrFail(2)).thenReturn(target);
        when(userService.paginate(anyInt(), anyInt(), any()))
                .thenReturn(page(List.of(user(10, "Alice", "alice@example.com", 7))));
        when(courseService.chunks(List.of(8))).thenReturn(List.of(course(8, "安全课", 17)));
        UserCourseRecord record = new UserCourseRecord();
        record.setId(31);
        record.setUserId(10);
        record.setCourseId(8);
        when(userCourseRecordService.chunk(List.of(10), List.of(8))).thenReturn(List.of(record));

        JsonNode response = get(DEPARTMENT + "/2/users?page=2&size=5&name=Alice&course_ids=8");

        assertThat(response.at("/data/courses/8/title").asText()).isEqualTo("安全课");
        assertThat(response.at("/data/user_course_records/10/8/id").asInt()).isEqualTo(31);
        ArgumentCaptor<UserPaginateFilter> filter =
                ArgumentCaptor.forClass(UserPaginateFilter.class);
        verify(userService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getDepIds()).containsExactly(2, 1);
    }

    @Test
    void ldapSyncCoversDisabledBusySuccessAndFailure() throws Exception {
        grant(BPermissionConstant.DEPARTMENT_CUD);
        assertBusinessError(post(DEPARTMENT + "/ldap-sync", Map.of()), "未配置LDAP服务");

        when(ldapBus.enabledLDAP()).thenReturn(true);
        when(ldapBus.hasSyncInProgress()).thenReturn(true);
        assertBusinessError(post(DEPARTMENT + "/ldap-sync", Map.of()), "有正在进行的LDAP同步任务，请稍后再试");

        when(ldapBus.hasSyncInProgress()).thenReturn(false);
        when(ldapBus.syncAndRecord(1)).thenReturn(99);
        assertThat(post(DEPARTMENT + "/ldap-sync", Map.of()).at("/data/record_id").asInt())
                .isEqualTo(99);

        when(ldapBus.syncAndRecord(1)).thenThrow(new RuntimeException("directory offline"));
        assertBusinessError(
                post(DEPARTMENT + "/ldap-sync", Map.of()), "LDAP同步失败: directory offline");
    }

    private void grant(String permission) {
        permissions.put(permission, true);
    }

    private User user(int id, String name, String email, int avatar) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setAvatar(avatar);
        return user;
    }

    private Department department(int id, String name, String parentChain) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        department.setParentChain(parentChain);
        return department;
    }

    private Course course(int id, String title, int thumb) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setThumb(thumb);
        return course;
    }

    private <T> PaginationResult<T> page(List<T> data) {
        PaginationResult<T> page = new PaginationResult<>();
        page.setData(data);
        page.setTotal((long) data.size());
        return page;
    }

    private Map<String, Object> userPayload(String password) {
        return Map.of(
                "email",
                "new@example.com",
                "name",
                "New User",
                "avatar",
                7,
                "password",
                password,
                "id_card",
                "ID-1",
                "dep_ids",
                List.of(3));
    }

    private Map<String, Object> importPayload() {
        return Map.of(
                "users",
                List.of(
                        Map.of(
                                "email", "new@example.com",
                                "name", "New User",
                                "password", "secret",
                                "deps", "研发部",
                                "id_card", "ID-1")),
                "start_line",
                2);
    }

    private Map<String, Object> departmentPayload() {
        return Map.of("name", "研发部", "parent_id", 0, "sort", 10);
    }

    private Map<String, Object> parentPayload() {
        return Map.of("id", 2, "parent_id", 0, "ids", List.of(2, 3));
    }

    private Request request(HttpMethod method, String path) {
        return new Request(method, path, null);
    }

    private JsonNode get(String path) {
        return read(getRaw(path));
    }

    private JsonNode post(String path, Object body) {
        return read(postRaw(path, body));
    }

    private JsonNode put(String path, Object body) {
        return read(putRaw(path, body));
    }

    private JsonNode delete(String path) {
        return read(deleteRaw(path));
    }

    private ResponseEntity<String> getRaw(String path) {
        return exchange(HttpMethod.GET, path, null, TOKEN);
    }

    private ResponseEntity<String> postRaw(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, TOKEN);
    }

    private ResponseEntity<String> putRaw(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body, TOKEN);
    }

    private ResponseEntity<String> deleteRaw(String path) {
        return exchange(HttpMethod.DELETE, path, null, TOKEN);
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
