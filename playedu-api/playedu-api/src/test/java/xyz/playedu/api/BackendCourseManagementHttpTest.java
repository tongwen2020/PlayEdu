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
import xyz.playedu.api.controller.backend.CourseAttachmentController;
import xyz.playedu.api.controller.backend.CourseAttachmentDownloadLogController;
import xyz.playedu.api.controller.backend.CourseChapterController;
import xyz.playedu.api.controller.backend.CourseController;
import xyz.playedu.api.controller.backend.CourseHourController;
import xyz.playedu.api.controller.backend.CourseUserController;
import xyz.playedu.api.interceptor.AdminInterceptor;
import xyz.playedu.api.interceptor.ApiInterceptor;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.constant.BPermissionConstant;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.domain.Category;
import xyz.playedu.common.domain.Department;
import xyz.playedu.common.domain.User;
import xyz.playedu.common.service.AdminUserService;
import xyz.playedu.common.service.AppConfigService;
import xyz.playedu.common.service.BackendAuthService;
import xyz.playedu.common.service.CategoryService;
import xyz.playedu.common.service.DepartmentService;
import xyz.playedu.common.service.RateLimiterService;
import xyz.playedu.common.service.UserService;
import xyz.playedu.common.types.paginate.CourseAttachmentDownloadLogPaginateFiler;
import xyz.playedu.common.types.paginate.CoursePaginateFiler;
import xyz.playedu.common.types.paginate.PaginationResult;
import xyz.playedu.common.types.paginate.UserPaginateFilter;
import xyz.playedu.course.domain.Course;
import xyz.playedu.course.domain.CourseAttachment;
import xyz.playedu.course.domain.CourseAttachmentDownloadLog;
import xyz.playedu.course.domain.CourseChapter;
import xyz.playedu.course.domain.CourseHour;
import xyz.playedu.course.domain.UserCourseRecord;
import xyz.playedu.course.service.CourseAttachmentDownloadLogService;
import xyz.playedu.course.service.CourseAttachmentService;
import xyz.playedu.course.service.CourseChapterService;
import xyz.playedu.course.service.CourseHourService;
import xyz.playedu.course.service.CourseService;
import xyz.playedu.course.service.UserCourseHourRecordService;
import xyz.playedu.course.service.UserCourseRecordService;
import xyz.playedu.resource.service.ResourceService;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/** Real HTTP business coverage for all backend course-management controllers. */
@SpringBootTest(
        classes = BackendCourseManagementHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "playedu.core.testing=false",
            "playedu.limiter.duration=60",
            "playedu.limiter.limit=100"
        })
class BackendCourseManagementHttpTest {
    private static final String COURSE = "/backend/v1/course";
    private static final String TOKEN = "backend-course-token";

    @Configuration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({
        CourseController.class,
        CourseChapterController.class,
        CourseHourController.class,
        CourseAttachmentController.class,
        CourseAttachmentDownloadLogController.class,
        CourseUserController.class,
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
    @MockBean private CourseService courseService;
    @MockBean private CategoryService categoryService;
    @MockBean private CourseChapterService chapterService;
    @MockBean private CourseHourService hourService;
    @MockBean private CourseAttachmentService attachmentService;
    @MockBean private CourseAttachmentDownloadLogService downloadLogService;
    @MockBean private ResourceService resourceService;
    @MockBean private DepartmentService departmentService;
    @MockBean private AdminUserService adminUserService;
    @MockBean private UserService userService;
    @MockBean private UserCourseRecordService userCourseRecordService;
    @MockBean private UserCourseHourRecordService userCourseHourRecordService;
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
        currentAdmin = new AdminUser();
        currentAdmin.setId(1);
        currentAdmin.setName("Course Admin");
        currentAdmin.setEmail("course-admin@example.com");
        currentAdmin.setIsBanLogin(0);

        when(appConfigService.keyValues()).thenReturn(Map.of());
        when(rateLimiterService.current(anyString(), anyLong())).thenReturn(0L);
        when(authService.check()).thenAnswer(invocation -> authenticated);
        when(authService.userId()).thenReturn(1);
        when(adminUserService.findById(1)).thenReturn(currentAdmin);
        when(backendBus.adminUserPermissions(1))
                .thenAnswer(invocation -> new HashMap<>(permissions));
        when(backendBus.isSuperAdmin()).thenReturn(false);

        when(categoryService.groupByParent()).thenReturn(Map.of());
        when(categoryService.id2name()).thenReturn(Map.of());
        when(departmentService.id2name()).thenReturn(Map.of());
        when(courseService.getCategoryIdsGroup(anyList())).thenReturn(Map.of());
        when(courseService.getDepIdsGroup(anyList())).thenReturn(Map.of());
        when(courseService.getDepIdsByCourseId(anyInt())).thenReturn(List.of());
        when(courseService.getCategoryIdsByCourseId(anyInt())).thenReturn(List.of());
        when(chapterService.getChaptersByCourseId(anyInt())).thenReturn(List.of());
        when(hourService.getHoursByCourseId(anyInt())).thenReturn(List.of());
        when(hourService.getRidsByCourseId(anyInt(), anyString())).thenReturn(List.of());
        when(attachmentService.getAttachmentsByCourseId(anyInt())).thenReturn(List.of());
        when(attachmentService.getRidsByCourseId(anyInt())).thenReturn(List.of());
        when(resourceService.chunksPreSignUrlByIds(anyList())).thenReturn(Map.of());
        when(userService.getDepIdsGroup(anyList())).thenReturn(Map.of());
        when(userCourseRecordService.chunk(anyList(), anyList())).thenReturn(List.of());
        when(userCourseHourRecordService.getUserCourseHourUserFirstCreatedAt(anyInt(), anyList()))
                .thenReturn(List.of());
        when(userCourseHourRecordService.getCoursePerUserEarliestRecord(anyInt()))
                .thenReturn(List.of());
    }

    @Test
    void allCourseEndpointsRequireAuthentication() {
        authenticated = false;
        List<Request> requests =
                List.of(
                        request(HttpMethod.GET, COURSE + "/index"),
                        request(HttpMethod.GET, COURSE + "/create"),
                        new Request(HttpMethod.POST, COURSE + "/create", coursePayload()),
                        request(HttpMethod.GET, COURSE + "/8"),
                        new Request(HttpMethod.PUT, COURSE + "/8", coursePayload()),
                        request(HttpMethod.DELETE, COURSE + "/8"),
                        new Request(
                                HttpMethod.POST, COURSE + "/8/chapter/create", chapterPayload()),
                        request(HttpMethod.GET, COURSE + "/8/chapter/3"),
                        new Request(HttpMethod.PUT, COURSE + "/8/chapter/3", chapterPayload()),
                        request(HttpMethod.DELETE, COURSE + "/8/chapter/3"),
                        new Request(
                                HttpMethod.PUT,
                                COURSE + "/8/chapter/update/sort",
                                Map.of("ids", List.of(3))),
                        request(HttpMethod.GET, COURSE + "/8/hour/create"),
                        new Request(HttpMethod.POST, COURSE + "/8/hour/create", hourPayload()),
                        new Request(
                                HttpMethod.POST,
                                COURSE + "/8/hour/create-batch",
                                hourBatchPayload()),
                        request(HttpMethod.GET, COURSE + "/8/hour/9"),
                        new Request(HttpMethod.PUT, COURSE + "/8/hour/9", hourPayload()),
                        request(HttpMethod.DELETE, COURSE + "/8/hour/9"),
                        new Request(
                                HttpMethod.PUT,
                                COURSE + "/8/hour/update/sort",
                                Map.of("ids", List.of(9))),
                        new Request(
                                HttpMethod.POST,
                                COURSE + "/8/attachment/create",
                                attachmentPayload()),
                        new Request(
                                HttpMethod.POST,
                                COURSE + "/8/attachment/create-batch",
                                attachmentBatchPayload()),
                        request(HttpMethod.GET, COURSE + "/8/attachment/6"),
                        new Request(
                                HttpMethod.PUT, COURSE + "/8/attachment/6", attachmentPayload()),
                        request(HttpMethod.DELETE, COURSE + "/8/attachment/6"),
                        new Request(
                                HttpMethod.PUT,
                                COURSE + "/8/attachment/update/sort",
                                Map.of("ids", List.of(6))),
                        request(HttpMethod.GET, COURSE + "/attachment/download/log/index"),
                        request(HttpMethod.GET, COURSE + "/8/user/index"),
                        new Request(
                                HttpMethod.POST,
                                COURSE + "/8/user/destroy",
                                Map.of("ids", List.of(31))));

        for (Request request : requests) {
            ResponseEntity<String> response =
                    exchange(request.method(), request.path(), request.body(), null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(read(response).get("msg").asText()).isEqualTo("请登录");
        }
    }

    @Test
    void coursePermissionGroupsAreEnforced() {
        assertForbidden(getRaw(COURSE + "/index"));
        assertForbidden(getRaw(COURSE + "/8/user/index"));
        assertForbidden(postRaw(COURSE + "/8/user/destroy", Map.of("ids", List.of(31))));

        verifyNoInteractions(courseService);
    }

    @Test
    void courseIndexAppliesHierarchyFiltersAndReturnsOperators() {
        grant(BPermissionConstant.COURSE);
        Course listed = course(8, "安全课", 1);
        listed.setThumb(17);
        when(departmentService.getChildDepartmentsByParentId(2))
                .thenReturn(List.of(department(3, "平台组")));
        when(categoryService.getChildCategorysByParentId(4)).thenReturn(List.of(category(5, "必修")));
        when(courseService.paginate(anyInt(), anyInt(), any())).thenReturn(page(List.of(listed)));
        when(adminUserService.chunks(List.of(1))).thenReturn(List.of(currentAdmin));
        when(resourceService.chunksPreSignUrlByIds(List.of(17)))
                .thenReturn(Map.of(17, "https://example/thumb"));

        JsonNode response = get(COURSE + "/index?page=2&size=5&title=安全&dep_ids=2&category_ids=4");

        assertThat(response.at("/data/data/0/title").asText()).isEqualTo("安全课");
        assertThat(response.at("/data/admin_users/1").asText()).isEqualTo("Course Admin");
        ArgumentCaptor<CoursePaginateFiler> filter =
                ArgumentCaptor.forClass(CoursePaginateFiler.class);
        verify(courseService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getDepIds()).containsExactlyInAnyOrder(2, 3);
        assertThat(filter.getValue().getCategoryIds()).containsExactlyInAnyOrder(4, 5);
        assertThat(filter.getValue().getAdminId()).isEqualTo(1);
    }

    @Test
    void courseCreatePageAndRequestValidationAreCovered() {
        grant(BPermissionConstant.COURSE);
        assertThat(get(COURSE + "/create").at("/data/categories").isObject()).isTrue();

        JsonNode invalid = post(COURSE + "/create", Map.of("title", ""));
        assertThat(invalid.get("code").asInt()).isEqualTo(406);

        Map<String, Object> longDescription = new HashMap<>(coursePayload());
        longDescription.put("short_desc", "x".repeat(201));
        assertBusinessError(post(COURSE + "/create", longDescription), "课程简短介绍不能超过200字");

        Map<String, Object> noHours = new HashMap<>(coursePayload());
        noHours.put("hours", List.of());
        noHours.put("chapters", List.of());
        when(courseService.createWithCategoryIdsAndDepIds(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt()))
                .thenReturn(course(8, "安全课", 1));
        assertBusinessError(post(COURSE + "/create", noHours), "请配置课时");
    }

    @Test
    void courseCanBeCreatedWithHoursAndAttachments() throws Exception {
        grant(BPermissionConstant.COURSE);
        when(courseService.createWithCategoryIdsAndDepIds(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any(),
                        anyInt()))
                .thenReturn(course(8, "安全课", 1));

        assertSuccess(post(COURSE + "/create", coursePayload()));

        verify(hourService).saveBatch(argThat(items -> items.size() == 1));
        verify(attachmentService).saveBatch(argThat(items -> items.size() == 1));
        verify(courseService).updateClassHour(8, 1);
    }

    @Test
    void courseOwnershipProtectsReadUpdateAndDelete() throws Exception {
        grant(BPermissionConstant.COURSE);
        Course other = course(8, "安全课", 2);
        when(courseService.findOrFail(8)).thenReturn(other);

        assertBusinessError(get(COURSE + "/8"), "无权限操作");
        assertBusinessError(put(COURSE + "/8", coursePayload()), "无权限操作");
        assertBusinessError(delete(COURSE + "/8"), "无权限操作");

        verify(courseService, never()).removeById(8);
    }

    @Test
    void courseOwnerCanReadUpdateAndDelete() throws Exception {
        grant(BPermissionConstant.COURSE);
        Course owned = course(8, "安全课", 1);
        owned.setThumb(17);
        when(courseService.findOrFail(8)).thenReturn(owned);

        JsonNode detail = get(COURSE + "/8");
        assertThat(detail.at("/data/course/title").asText()).isEqualTo("安全课");

        assertSuccess(put(COURSE + "/8", coursePayload()));
        verify(courseService)
                .updateWithCategoryIdsAndDepIds(
                        eq(owned),
                        eq("安全课"),
                        eq(17),
                        eq("课程简介"),
                        eq(1),
                        eq(1),
                        eq("2026-09-08 12:00:00"),
                        any(),
                        any());

        assertSuccess(delete(COURSE + "/8"));
        verify(courseService).removeById(8);
    }

    @Test
    void chapterCrudSortAndDeleteProtectionAreCovered() throws Exception {
        grant(BPermissionConstant.COURSE);
        CourseChapter chapter = chapter(3, 8, "第一章");
        when(chapterService.findOrFail(3, 8)).thenReturn(chapter);

        assertSuccess(post(COURSE + "/8/chapter/create", chapterPayload()));
        verify(chapterService).create(8, "第一章", 1);
        assertThat(get(COURSE + "/8/chapter/3").at("/data/name").asText()).isEqualTo("第一章");
        assertSuccess(put(COURSE + "/8/chapter/3", chapterPayload()));
        verify(chapterService).update(chapter, "第一章", 1);

        when(hourService.getCountByChapterId(3)).thenReturn(1);
        assertBusinessError(delete(COURSE + "/8/chapter/3"), "当前章节下面存在课时无法删除");
        when(hourService.getCountByChapterId(3)).thenReturn(0);
        assertSuccess(delete(COURSE + "/8/chapter/3"));
        verify(chapterService).removeById(3);

        assertSuccess(put(COURSE + "/8/chapter/update/sort", Map.of("ids", List.of(3))));
        verify(chapterService).updateSort(List.of(3), 8);
    }

    @Test
    void hourCreateCoversMetadataTypeDuplicateAndSuccess() throws Exception {
        grant(BPermissionConstant.COURSE);
        CourseChapter chapter = chapter(3, 8, "第一章");
        when(chapterService.getChaptersByCourseId(8)).thenReturn(List.of(chapter));
        assertThat(get(COURSE + "/8/hour/create").at("/data/types/0/key").asText())
                .isEqualTo("VIDEO");

        Map<String, Object> unsupported = new HashMap<>(hourPayload());
        unsupported.put("type", "AUDIO");
        assertBusinessError(post(COURSE + "/8/hour/create", unsupported), "课时类型不支持");

        when(chapterService.findOrFail(3, 8)).thenReturn(chapter);
        when(hourService.getRidsByCourseId(8, "VIDEO")).thenReturn(List.of(21));
        assertBusinessError(post(COURSE + "/8/hour/create", hourPayload()), "课时已存在");

        when(hourService.getRidsByCourseId(8, "VIDEO")).thenReturn(List.of());
        when(hourService.create(8, 3, 1, "第一课时", "VIDEO", 21, 600))
                .thenReturn(hour(9, 8, 3, "第一课时"));
        assertSuccess(post(COURSE + "/8/hour/create", hourPayload()));
    }

    @Test
    void hourBatchCoversEmptyDuplicateAndSuccess() {
        grant(BPermissionConstant.COURSE);
        assertBusinessError(
                post(COURSE + "/8/hour/create-batch", Map.of("hours", List.of())), "参数为空");

        when(hourService.getRidsByCourseId(8, "VIDEO")).thenReturn(List.of(21));
        assertBusinessError(
                post(COURSE + "/8/hour/create-batch", hourBatchPayload()), "课时《第一课时》已存在");

        when(hourService.getRidsByCourseId(8, "VIDEO")).thenReturn(List.of());
        assertSuccess(post(COURSE + "/8/hour/create-batch", hourBatchPayload()));
        verify(hourService).saveBatch(argThat(items -> items.size() == 1));
    }

    @Test
    void hourCanBeReadUpdatedDeletedAndSorted() throws Exception {
        grant(BPermissionConstant.COURSE);
        CourseHour hour = hour(9, 8, 3, "第一课时");
        when(hourService.findOrFail(9, 8)).thenReturn(hour);
        when(chapterService.findOrFail(3, 8)).thenReturn(chapter(3, 8, "第一章"));

        assertThat(get(COURSE + "/8/hour/9").at("/data/title").asText()).isEqualTo("第一课时");
        assertSuccess(put(COURSE + "/8/hour/9", hourPayload()));
        verify(hourService).update(hour, 3, 1, "第一课时", 600);
        assertSuccess(delete(COURSE + "/8/hour/9"));
        verify(hourService).removeById(9);
        assertSuccess(put(COURSE + "/8/hour/update/sort", Map.of("ids", List.of(9))));
        verify(hourService).updateSort(List.of(9), 8);
    }

    @Test
    void attachmentCreateCoversTypeDuplicateAndSuccess() throws Exception {
        grant(BPermissionConstant.COURSE);
        Map<String, Object> unsupported = new HashMap<>(attachmentPayload());
        unsupported.put("type", "VIDEO");
        assertBusinessError(post(COURSE + "/8/attachment/create", unsupported), "附件类型不支持");

        when(attachmentService.getRidsByCourseId(8)).thenReturn(List.of(22));
        assertBusinessError(post(COURSE + "/8/attachment/create", attachmentPayload()), "附件已存在");

        when(attachmentService.getRidsByCourseId(8)).thenReturn(List.of());
        assertSuccess(post(COURSE + "/8/attachment/create", attachmentPayload()));
        verify(attachmentService).create(8, 1, "课件", "PDF", 22);
    }

    @Test
    void attachmentBatchCoversEmptyDuplicateAndSuccess() {
        grant(BPermissionConstant.COURSE);
        assertBusinessError(
                post(COURSE + "/8/attachment/create-batch", Map.of("attachments", List.of())),
                "参数为空");

        when(attachmentService.getRidsByCourseId(8)).thenReturn(List.of(22));
        assertBusinessError(
                post(COURSE + "/8/attachment/create-batch", attachmentBatchPayload()), "附件《课件》已存在");

        when(attachmentService.getRidsByCourseId(8)).thenReturn(List.of());
        assertSuccess(post(COURSE + "/8/attachment/create-batch", attachmentBatchPayload()));
        verify(attachmentService).saveBatch(argThat(items -> items.size() == 1));
    }

    @Test
    void attachmentCanBeReadUpdatedDeletedAndSorted() throws Exception {
        grant(BPermissionConstant.COURSE);
        CourseAttachment attachment = new CourseAttachment();
        attachment.setId(6);
        attachment.setCourseId(8);
        attachment.setTitle("课件");
        when(attachmentService.findOrFail(6, 8)).thenReturn(attachment);

        assertThat(get(COURSE + "/8/attachment/6").at("/data/title").asText()).isEqualTo("课件");
        assertSuccess(put(COURSE + "/8/attachment/6", attachmentPayload()));
        verify(attachmentService).update(attachment, 1, "课件");
        assertSuccess(delete(COURSE + "/8/attachment/6"));
        verify(attachmentService).removeById(6);
        assertSuccess(put(COURSE + "/8/attachment/update/sort", Map.of("ids", List.of(6))));
        verify(attachmentService).updateSort(List.of(6), 8);
    }

    @Test
    void attachmentDownloadLogUsesPaginationFiltersWithoutCoursePermission() {
        when(downloadLogService.paginate(anyInt(), anyInt(), any()))
                .thenReturn(page(List.of(new CourseAttachmentDownloadLog())));

        JsonNode response =
                get(
                        COURSE
                                + "/attachment/download/log/index?page=2&size=5&user_id=10&course_id=8&title=课件&rid=22");

        assertThat(response.at("/data/total").asLong()).isEqualTo(1);
        ArgumentCaptor<CourseAttachmentDownloadLogPaginateFiler> filter =
                ArgumentCaptor.forClass(CourseAttachmentDownloadLogPaginateFiler.class);
        verify(downloadLogService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getUserId()).isEqualTo(10);
        assertThat(filter.getValue().getCourseId()).isEqualTo(8);
        assertThat(filter.getValue().getRid()).isEqualTo(22);
    }

    @Test
    void courseUserIndexReturnsCourseAndLearningRecords() throws Exception {
        grant(BPermissionConstant.COURSE_USER);
        User user = new User();
        user.setId(10);
        user.setName("Alice");
        user.setAvatar(7);
        when(userService.paginate(anyInt(), anyInt(), any())).thenReturn(page(List.of(user)));
        Course owned = course(8, "安全课", 1);
        when(courseService.findOrFail(8)).thenReturn(owned);
        UserCourseRecord record = new UserCourseRecord();
        record.setId(31);
        record.setUserId(10);
        record.setCourseId(8);
        when(userCourseRecordService.chunk(List.of(10), List.of(8))).thenReturn(List.of(record));

        JsonNode response = get(COURSE + "/8/user/index?page=2&size=5&dep_id=3");

        assertThat(response.at("/data/course/title").asText()).isEqualTo("安全课");
        assertThat(response.at("/data/user_course_records/10/id").asInt()).isEqualTo(31);
        ArgumentCaptor<UserPaginateFilter> filter =
                ArgumentCaptor.forClass(UserPaginateFilter.class);
        verify(userService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getDepIds()).containsExactly(3);
    }

    @Test
    void courseLearningRecordsRequireSelectionAndCanBeDeleted() {
        grant(BPermissionConstant.COURSE_USER_DESTROY);
        assertBusinessError(
                post(COURSE + "/8/user/destroy", Map.of("ids", List.of())), "请选择需要删除的数据");

        UserCourseRecord record = new UserCourseRecord();
        record.setId(31);
        record.setUserId(10);
        when(userCourseRecordService.chunks(eq(List.of(31)), anyList()))
                .thenReturn(List.of(record));
        assertSuccess(post(COURSE + "/8/user/destroy", Map.of("ids", List.of(31))));
        verify(userCourseRecordService).removeById(record);
    }

    private void grant(String permission) {
        permissions.put(permission, true);
    }

    private Course course(int id, String title, int adminId) {
        Course course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setAdminId(adminId);
        return course;
    }

    private Department department(int id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        return department;
    }

    private Category category(int id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private CourseChapter chapter(int id, int courseId, String name) {
        CourseChapter chapter = new CourseChapter();
        chapter.setId(id);
        chapter.setCourseId(courseId);
        chapter.setName(name);
        return chapter;
    }

    private CourseHour hour(int id, int courseId, int chapterId, String title) {
        CourseHour hour = new CourseHour();
        hour.setId(id);
        hour.setCourseId(courseId);
        hour.setChapterId(chapterId);
        hour.setTitle(title);
        return hour;
    }

    private <T> PaginationResult<T> page(List<T> data) {
        PaginationResult<T> page = new PaginationResult<>();
        page.setData(data);
        page.setTotal((long) data.size());
        return page;
    }

    private Map<String, Object> coursePayload() {
        return Map.ofEntries(
                Map.entry("title", "安全课"),
                Map.entry("thumb", 17),
                Map.entry("short_desc", "课程简介"),
                Map.entry("is_show", 1),
                Map.entry("is_required", 1),
                Map.entry("dep_ids", List.of(2)),
                Map.entry("category_ids", List.of(4)),
                Map.entry("sort_at", "2026-09-08 12:00:00"),
                Map.entry("chapters", List.of()),
                Map.entry(
                        "hours",
                        List.of(
                                Map.of(
                                        "name",
                                        "第一课时",
                                        "type",
                                        "VIDEO",
                                        "duration",
                                        600,
                                        "rid",
                                        21))),
                Map.entry("attachments", List.of(Map.of("name", "课件", "type", "PDF", "rid", 22))));
    }

    private Map<String, Object> chapterPayload() {
        return Map.of("name", "第一章", "sort", 1);
    }

    private Map<String, Object> hourPayload() {
        return Map.of(
                "chapter_id", 3,
                "title", "第一课时",
                "duration", 600,
                "sort", 1,
                "type", "VIDEO",
                "rid", 21);
    }

    private Map<String, Object> hourBatchPayload() {
        return Map.of(
                "hours",
                List.of(
                        Map.of(
                                "chapter_id", 3,
                                "title", "第一课时",
                                "duration", 600,
                                "sort", 1,
                                "type", "VIDEO",
                                "rid", 21)));
    }

    private Map<String, Object> attachmentPayload() {
        return Map.of("title", "课件", "sort", 1, "type", "PDF", "rid", 22);
    }

    private Map<String, Object> attachmentBatchPayload() {
        return Map.of(
                "attachments", List.of(Map.of("title", "课件", "sort", 1, "type", "PDF", "rid", 22)));
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
        return read(exchange(HttpMethod.PUT, path, body, TOKEN));
    }

    private JsonNode delete(String path) {
        return read(exchange(HttpMethod.DELETE, path, null, TOKEN));
    }

    private ResponseEntity<String> getRaw(String path) {
        return exchange(HttpMethod.GET, path, null, TOKEN);
    }

    private ResponseEntity<String> postRaw(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, TOKEN);
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
