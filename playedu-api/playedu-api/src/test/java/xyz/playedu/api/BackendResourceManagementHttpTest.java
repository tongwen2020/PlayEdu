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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.playedu.api.controller.ExceptionController;
import xyz.playedu.api.controller.backend.ResourceCategoryController;
import xyz.playedu.api.controller.backend.ResourceController;
import xyz.playedu.api.controller.backend.UploadController;
import xyz.playedu.api.interceptor.AdminInterceptor;
import xyz.playedu.api.interceptor.ApiInterceptor;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.constant.BPermissionConstant;
import xyz.playedu.common.constant.BackendConstant;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.domain.Category;
import xyz.playedu.common.service.AdminUserService;
import xyz.playedu.common.service.AppConfigService;
import xyz.playedu.common.service.BackendAuthService;
import xyz.playedu.common.service.CategoryService;
import xyz.playedu.common.service.RateLimiterService;
import xyz.playedu.common.types.UploadFileInfo;
import xyz.playedu.common.types.config.S3Config;
import xyz.playedu.common.types.paginate.PaginationResult;
import xyz.playedu.common.types.paginate.ResourcePaginateFilter;
import xyz.playedu.course.domain.Course;
import xyz.playedu.course.service.CourseCategoryService;
import xyz.playedu.course.service.CourseService;
import xyz.playedu.resource.domain.Resource;
import xyz.playedu.resource.domain.ResourceExtra;
import xyz.playedu.resource.service.ResourceCategoryService;
import xyz.playedu.resource.service.ResourceExtraService;
import xyz.playedu.resource.service.ResourceService;
import xyz.playedu.resource.service.UploadService;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/** Real HTTP business coverage for backend resource, category, and upload controllers. */
@SpringBootTest(
        classes = BackendResourceManagementHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "playedu.core.testing=false",
            "playedu.limiter.duration=60",
            "playedu.limiter.limit=100"
        })
class BackendResourceManagementHttpTest {
    private static final String RESOURCE = "/backend/v1/resource";
    private static final String CATEGORY = "/backend/v1/resource-category";
    private static final String UPLOAD = "/backend/v1/upload";
    private static final String TOKEN = "backend-resource-token";

    @Configuration
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({
        ResourceController.class,
        ResourceCategoryController.class,
        UploadController.class,
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
    @MockBean private CategoryService categoryService;
    @MockBean private CourseService courseService;
    @MockBean private CourseCategoryService courseCategoryService;
    @MockBean private ResourceService resourceService;
    @MockBean private ResourceExtraService resourceExtraService;
    @MockBean private ResourceCategoryService resourceCategoryService;
    @MockBean private UploadService uploadService;

    private final HashMap<String, Boolean> permissions = new HashMap<>();
    private boolean authenticated;

    @BeforeEach
    void setUp() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        authenticated = true;
        permissions.clear();

        AdminUser admin = new AdminUser();
        admin.setId(1);
        admin.setName("Resource Admin");
        admin.setEmail("resource-admin@example.com");
        admin.setIsBanLogin(0);

        when(appConfigService.keyValues()).thenReturn(Map.of());
        when(appConfigService.getS3Config()).thenReturn(validS3Config());
        when(rateLimiterService.current(anyString(), anyLong())).thenReturn(0L);
        when(authService.check()).thenAnswer(invocation -> authenticated);
        when(authService.userId()).thenReturn(1);
        when(adminUserService.findById(1)).thenReturn(admin);
        when(backendBus.adminUserPermissions(1))
                .thenAnswer(invocation -> new HashMap<>(permissions));
        when(backendBus.isSuperAdmin()).thenReturn(false);
        when(resourceService.chunksPreSignUrlByIds(anyList())).thenReturn(Map.of());
        when(categoryService.groupByParent()).thenReturn(Map.of());
    }

    @Test
    void allResourceAndUploadEndpointsRequireAuthentication() {
        authenticated = false;
        List<Request> requests =
                List.of(
                        request(HttpMethod.GET, RESOURCE + "/index?type=VIDEO"),
                        request(HttpMethod.DELETE, RESOURCE + "/8"),
                        new Request(
                                HttpMethod.POST,
                                RESOURCE + "/destroy-multi",
                                Map.of("ids", List.of(8))),
                        request(HttpMethod.GET, RESOURCE + "/8"),
                        new Request(
                                HttpMethod.PUT,
                                RESOURCE + "/8",
                                Map.of("name", "课件", "category_id", 2)),
                        request(HttpMethod.GET, CATEGORY + "/index"),
                        request(HttpMethod.GET, CATEGORY + "/categories"),
                        request(HttpMethod.GET, CATEGORY + "/create"),
                        new Request(HttpMethod.POST, CATEGORY + "/create", categoryPayload()),
                        request(HttpMethod.GET, CATEGORY + "/2"),
                        new Request(HttpMethod.PUT, CATEGORY + "/2", categoryPayload()),
                        request(HttpMethod.GET, CATEGORY + "/2/destroy"),
                        request(HttpMethod.DELETE, CATEGORY + "/2"),
                        new Request(
                                HttpMethod.PUT,
                                CATEGORY + "/update/sort",
                                Map.of("ids", List.of(2))),
                        new Request(
                                HttpMethod.PUT,
                                CATEGORY + "/update/parent",
                                Map.of("id", 2, "parent_id", 0, "ids", List.of(2))),
                        new Request(HttpMethod.POST, UPLOAD + "/minio", Map.of()),
                        request(HttpMethod.GET, UPLOAD + "/minio/upload-id?extension=pdf"),
                        request(
                                HttpMethod.GET,
                                UPLOAD
                                        + "/minio/pre-sign-url?upload_id=u1&part_number=1&filename=file.pdf"),
                        request(
                                HttpMethod.GET,
                                UPLOAD + "/minio/list-parts?upload_id=u1&filename=file.pdf"),
                        request(
                                HttpMethod.GET,
                                UPLOAD + "/minio/purge-segments?upload_id=u1&filename=file.pdf"),
                        new Request(HttpMethod.POST, UPLOAD + "/minio/merge-file", mergePayload()));

        for (Request request : requests) {
            ResponseEntity<String> response =
                    exchange(request.method(), request.path(), request.body(), null);
            assertThat(response.getStatusCode().value()).isEqualTo(401);
            assertThat(read(response).get("msg").asText()).isEqualTo("请登录");
        }
    }

    @Test
    void protectedResourceCategoryAndUploadEndpointsEnforcePermissions() {
        assertForbidden(postRaw(CATEGORY + "/create", categoryPayload()));
        assertForbidden(getRaw(CATEGORY + "/2"));
        assertForbidden(getRaw(UPLOAD + "/minio/upload-id?extension=pdf"));
        assertForbidden(postRaw(UPLOAD + "/minio/merge-file", mergePayload()));

        verifyNoInteractions(uploadService);
    }

    @Test
    void resourceIndexValidatesTypeAndAppliesCategoryAndOwnerFilters() {
        assertBusinessError(get(RESOURCE + "/index"), "请选择资源类型");

        Category child = category(3, "子分类");
        Resource resource = resource(8, 1, BackendConstant.RESOURCE_TYPE_VIDEO, "培训视频");
        when(categoryService.getChildCategorysByParentId(2)).thenReturn(List.of(child));
        when(resourceService.paginate(anyInt(), anyInt(), any()))
                .thenReturn(page(List.of(resource)));
        ResourceExtra extra = new ResourceExtra();
        extra.setRid(8);
        when(resourceExtraService.chunksByRids(List.of(8))).thenReturn(List.of(extra));
        when(resourceService.chunksPreSignUrlByIds(List.of(8)))
                .thenReturn(Map.of(8, "https://example/video"));
        when(adminUserService.chunks(List.of(1)))
                .thenReturn(List.of(adminUser(1, "Resource Admin")));

        JsonNode response =
                get(RESOURCE + "/index?page=2&size=5&type=VIDEO&category_ids=2&name=培训");

        assertThat(response.at("/data/result/data/0/name").asText()).isEqualTo("培训视频");
        ArgumentCaptor<ResourcePaginateFilter> filter =
                ArgumentCaptor.forClass(ResourcePaginateFilter.class);
        verify(resourceService).paginate(eq(2), eq(5), filter.capture());
        assertThat(filter.getValue().getCategoryIds()).containsExactlyInAnyOrder(2, 3);
        assertThat(filter.getValue().getAdminId()).isEqualTo(1);
        assertThat(filter.getValue().getName()).isEqualTo("培训");
    }

    @Test
    void attachmentResourceIndexReturnsExistingFileTypes() {
        when(resourceService.paginate(anyInt(), anyInt(), any())).thenReturn(page(List.of()));
        when(resourceService.paginateType(any())).thenReturn(List.of("PDF", "WORD"));

        JsonNode response = get(RESOURCE + "/index?type=PDF&category_ids=0");

        assertThat(response.at("/data/existing_types/0").asText()).isEqualTo("PDF");
        ArgumentCaptor<ResourcePaginateFilter> filter =
                ArgumentCaptor.forClass(ResourcePaginateFilter.class);
        verify(resourceService).paginateType(filter.capture());
        assertThat(filter.getValue().getType()).isEqualTo(BackendConstant.RESOURCE_TYPE_ATTACHMENT);
        assertThat(filter.getValue().getCategoryIds()).containsOnly(0);
    }

    @Test
    void resourceOwnershipProtectsReadUpdateAndDelete() throws Exception {
        Resource other = resource(8, 2, BackendConstant.RESOURCE_TYPE_IMAGE, "图片");
        when(resourceService.findOrFail(8)).thenReturn(other);

        assertBusinessError(get(RESOURCE + "/8"), "无权限");
        assertBusinessError(put(RESOURCE + "/8", Map.of("name", "新名称", "category_id", 2)), "无权限");
        assertBusinessError(delete(RESOURCE + "/8"), "无权限");

        verify(resourceService, never()).updateNameAndCategoryId(anyInt(), anyString(), anyInt());
        verify(resourceService, never()).removeById(anyInt());
    }

    @Test
    void resourceOwnerCanReadAndUpdate() throws Exception {
        Resource owned = resource(8, 1, BackendConstant.RESOURCE_TYPE_IMAGE, "图片");
        owned.setPath("images/demo.png");
        when(resourceService.findOrFail(8)).thenReturn(owned);
        when(resourceService.categoryIds(8)).thenReturn(List.of(2));

        assertThat(get(RESOURCE + "/8").at("/data/resources/name").asText()).isEqualTo("图片");
        assertSuccess(put(RESOURCE + "/8", Map.of("name", "新名称", "category_id", 2)));
        verify(resourceService).updateNameAndCategoryId(8, "新名称", 2);
    }

    @Test
    void resourceBatchDeleteValidatesSelectionAndHandlesMissingRecords() {
        assertBusinessError(
                post(RESOURCE + "/destroy-multi", Map.of("ids", List.of())), "请选择需要删除的资源");
        when(resourceService.chunks(List.of(8))).thenReturn(List.of());
        assertSuccess(post(RESOURCE + "/destroy-multi", Map.of("ids", List.of(8))));
    }

    @Test
    void categoryReadAndCreateFlowsAreCovered() throws Exception {
        grant(BPermissionConstant.RESOURCE_CATEGORY);
        Category category = category(2, "课件");
        when(categoryService.listByParentId(0)).thenReturn(List.of(category));

        assertThat(get(CATEGORY + "/index").at("/data/categories").isObject()).isTrue();
        assertThat(get(CATEGORY + "/categories?parent_id=0").at("/data/0/name").asText())
                .isEqualTo("课件");
        assertThat(get(CATEGORY + "/create").at("/data/categories").isObject()).isTrue();
        assertBusinessError(post(CATEGORY + "/create", Map.of()), "请输入分类名");
        assertSuccess(post(CATEGORY + "/create", categoryPayload()));
        verify(categoryService).create("课件", 0, 1);
    }

    @Test
    void categoryCanBeReadUpdatedDeletedSortedAndMoved() throws Exception {
        grant(BPermissionConstant.RESOURCE_CATEGORY);
        Category category = category(2, "课件");
        when(categoryService.findOrFail(2)).thenReturn(category);

        assertThat(get(CATEGORY + "/2").at("/data/name").asText()).isEqualTo("课件");
        assertSuccess(put(CATEGORY + "/2", categoryPayload()));
        verify(categoryService).update(category, "课件", 0, 1);

        assertSuccess(put(CATEGORY + "/update/sort", Map.of("ids", List.of(2))));
        verify(categoryService).resetSort(List.of(2));
        assertSuccess(
                put(
                        CATEGORY + "/update/parent",
                        Map.of("id", 2, "parent_id", 0, "ids", List.of(2))));
        verify(categoryService).changeParent(2, 0, List.of(2));

        assertSuccess(delete(CATEGORY + "/2"));
        verify(categoryService).deleteById(2);
    }

    @Test
    void categoryPreDeleteReturnsChildrenCoursesAndResources() {
        grant(BPermissionConstant.RESOURCE_CATEGORY);
        when(categoryService.listByParentId(2)).thenReturn(List.of(category(3, "子分类")));
        when(courseCategoryService.getCourseIdsByCategoryId(2)).thenReturn(List.of(7));
        Course course = new Course();
        course.setId(7);
        course.setTitle("安全课");
        when(courseService.chunks(eq(List.of(7)), anyList())).thenReturn(List.of(course));
        when(resourceCategoryService.getRidsByCategoryId(2)).thenReturn(List.of(8));
        when(resourceService.chunks(eq(List.of(8)), anyList()))
                .thenReturn(List.of(resource(8, 1, BackendConstant.RESOURCE_TYPE_VIDEO, "培训视频")));

        JsonNode response = get(CATEGORY + "/2/destroy");

        assertThat(response.at("/data/children/0/name").asText()).isEqualTo("子分类");
        assertThat(response.at("/data/courses/0/title").asText()).isEqualTo("安全课");
        assertThat(response.at("/data/videos/0/name").asText()).isEqualTo("培训视频");
    }

    @Test
    void uploadValidatesStorageConfigurationAndExtension() {
        grant(BPermissionConstant.UPLOAD);
        when(appConfigService.getS3Config()).thenReturn(new S3Config());

        assertBusinessError(get(UPLOAD + "/minio/upload-id?extension=pdf"), "存储服务未配置");

        when(appConfigService.getS3Config()).thenReturn(validS3Config());
        assertBusinessError(get(UPLOAD + "/minio/upload-id"), "extension参数为空");
        assertBusinessError(get(UPLOAD + "/minio/upload-id?extension=exe"), "该格式文件不支持上传");
        assertBusinessError(post(UPLOAD + "/minio/merge-file", mergePayload("exe")), "当前格式不支持上传");
    }

    @Test
    void multipartUploadCreatesAResource() throws Exception {
        grant(BPermissionConstant.UPLOAD);
        UploadFileInfo info = new UploadFileInfo();
        info.setOriginalName("manual.pdf");
        info.setExtension("pdf");
        info.setResourceType(BackendConstant.RESOURCE_TYPE_PDF);
        info.setSavePath("files/manual.pdf");
        when(uploadService.upload(any(), any(), isNull())).thenReturn(info);
        Resource saved = resource(8, 1, BackendConstant.RESOURCE_TYPE_PDF, "manual.pdf");
        when(resourceService.create(
                        eq(1),
                        eq("2"),
                        eq(BackendConstant.RESOURCE_TYPE_PDF),
                        eq("manual.pdf"),
                        eq("pdf"),
                        eq(6L),
                        eq(""),
                        eq("files/manual.pdf"),
                        eq(0),
                        eq(0)))
                .thenReturn(saved);

        JsonNode response = read(uploadMultipart("2"));

        assertThat(response.at("/data/id").asInt()).isEqualTo(8);
        verify(uploadService).upload(any(), any(), isNull());
    }

    @Test
    void multipartUploadRejectsIncompleteStorageConfiguration() {
        grant(BPermissionConstant.UPLOAD);
        when(appConfigService.getS3Config()).thenReturn(new S3Config());

        assertBusinessError(read(uploadMultipart("2")), "存储服务未配置");
        verifyNoInteractions(uploadService);
    }

    @Test
    void multipartUploadLifecycleRoutesValidateRequestsWithoutExternalStorageCalls() {
        grant(BPermissionConstant.UPLOAD);
        assertBusinessError(get(UPLOAD + "/minio/upload-id"), "extension参数为空");
        assertBusinessError(post(UPLOAD + "/minio/merge-file", mergePayload("exe")), "当前格式不支持上传");
    }

    private void grant(String permission) {
        permissions.put(permission, true);
    }

    private AdminUser adminUser(int id, String name) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setName(name);
        return admin;
    }

    private Category category(int id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private Resource resource(int id, int adminId, String type, String name) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setAdminId(adminId);
        resource.setType(type);
        resource.setName(name);
        return resource;
    }

    private <T> PaginationResult<T> page(List<T> data) {
        PaginationResult<T> page = new PaginationResult<>();
        page.setData(data);
        page.setTotal((long) data.size());
        return page;
    }

    private S3Config validS3Config() {
        S3Config config = new S3Config();
        config.setAccessKey("access");
        config.setSecretKey("secret");
        config.setBucket("bucket");
        config.setEndpoint("https://s3.example.com");
        config.setRegion("cn-test-1");
        return config;
    }

    private Map<String, Object> categoryPayload() {
        return Map.of("name", "课件", "parent_id", 0, "sort", 1);
    }

    private Map<String, Object> mergePayload() {
        return mergePayload("pdf");
    }

    private Map<String, Object> mergePayload(String extension) {
        return Map.of(
                "filename", "files/manual.pdf",
                "upload_id", "upload-1",
                "original_filename", "manual.pdf",
                "size", 6,
                "duration", 0,
                "extension", extension,
                "category_ids", "2",
                "poster", "");
    }

    private ResponseEntity<String> uploadMultipart(String categoryIds) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(
                "file",
                new ByteArrayResource("manual".getBytes()) {
                    @Override
                    public String getFilename() {
                        return "manual.pdf";
                    }
                });
        body.add("category_ids", categoryIds);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(TOKEN);
        return http.exchange(
                UPLOAD + "/minio", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
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
        assertThat(response.get("code").asInt()).isEqualTo(0);
    }

    private void assertBusinessError(JsonNode response, String message) {
        assertThat(response.get("code").asInt()).isNotEqualTo(0);
        assertThat(response.get("msg").asText()).contains(message);
    }

    private record Request(HttpMethod method, String path, Object body) {}
}
