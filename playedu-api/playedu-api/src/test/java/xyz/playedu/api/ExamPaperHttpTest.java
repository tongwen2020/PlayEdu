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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import xyz.playedu.api.controller.ExceptionController;
import xyz.playedu.api.controller.backend.*;
import xyz.playedu.api.interceptor.*;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.context.BCtx;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.service.*;
import xyz.playedu.exam.*;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/** Real HTTP acceptance tests against the MySQL instance on port 23307. */
@SpringBootTest(
        classes = ExamPaperHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
class ExamPaperHttpTest {
    private static final String PAPER = "/backend/v1/exam-paper";
    private static final String QUESTION = "/backend/v1/question-bank";
    private static final String MYSQL_HOST = env("EXAM_PAPER_TEST_DB_HOST", "127.0.0.1");
    private static final String MYSQL_PORT = env("EXAM_PAPER_TEST_DB_PORT", "23307");
    private static final String MYSQL_DATABASE =
            env("EXAM_PAPER_TEST_DB_NAME", "playedu_exam_paper_test");
    private static final String MYSQL_USER = env("EXAM_PAPER_TEST_DB_USER", "root");
    private static final String MYSQL_PASSWORD = env("EXAM_PAPER_TEST_DB_PASS", "playeduxyz");

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) throws Exception {
        if (!MYSQL_DATABASE.matches("[a-zA-Z0-9_]+_test"))
            throw new IllegalStateException("EXAM_PAPER_TEST_DB_NAME 必须以 _test 结尾");
        String serverUrl =
                "jdbc:mysql://"
                        + MYSQL_HOST
                        + ":"
                        + MYSQL_PORT
                        + "/?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
        try (var connection = DriverManager.getConnection(serverUrl, MYSQL_USER, MYSQL_PASSWORD);
                var statement = connection.createStatement()) {
            statement.execute(
                    "CREATE DATABASE IF NOT EXISTS `"
                            + MYSQL_DATABASE
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:mysql://"
                                + MYSQL_HOST
                                + ":"
                                + MYSQL_PORT
                                + "/"
                                + MYSQL_DATABASE
                                + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", () -> MYSQL_USER);
        registry.add("spring.datasource.password", () -> MYSQL_PASSWORD);
    }

    @AfterAll
    static void dropTemporaryMysqlDatabase() throws Exception {
        if (!MYSQL_DATABASE.matches("[a-zA-Z0-9_]+_test"))
            throw new IllegalStateException("拒绝删除非测试库");
        String serverUrl =
                "jdbc:mysql://"
                        + MYSQL_HOST
                        + ":"
                        + MYSQL_PORT
                        + "/?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
        try (var connection = DriverManager.getConnection(serverUrl, MYSQL_USER, MYSQL_PASSWORD);
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + MYSQL_DATABASE + "`");
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({
        ExamPaperController.class,
        QuestionBankController.class,
        ExamPaperService.class,
        QuestionBankService.class,
        QuestionGrader.class,
        QuestionBankMigration.class,
        ExamPaperMigration.class,
        QuestionBankMethodFilter.class,
        ExceptionController.class,
        BackendPermissionAspect.class,
        AdminInterceptor.class,
        FrontInterceptor.class,
        ApiInterceptor.class,
        WebMvcConfig.class,
        PlayEduConfig.class
    })
    static class Harness {
        @Bean
        @Order(0)
        CommandLineRunner baseSchema(JdbcTemplate db) {
            return args ->
                    db.execute(
                            "CREATE TABLE IF NOT EXISTS migrations(id INT AUTO_INCREMENT PRIMARY"
                                    + " KEY,migration VARCHAR(191) NOT NULL)");
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired ExamPaperMigration migration;
    @MockBean BackendAuthService adminAuth;
    @MockBean FrontendAuthService studentAuth;
    @MockBean AdminUserService admins;
    @MockBean UserService users;
    @MockBean BackendBus backendBus;
    @MockBean AppConfigService config;
    @MockBean RateLimiterService limiter;

    private long bankId;
    private JsonNode single;
    private JsonNode shortAnswer;

    private String token() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest()
                .getHeader("Authorization");
    }

    @BeforeEach
    void setUp() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        resetTables();
        when(config.keyValues()).thenReturn(Map.of());
        when(limiter.current(anyString(), anyLong())).thenReturn(0L);
        when(adminAuth.check())
                .thenAnswer(
                        ignored ->
                                Set.of("Bearer admin-1", "Bearer admin-2", "Bearer admin-3")
                                        .contains(Objects.toString(token(), "")));
        when(adminAuth.userId())
                .thenAnswer(
                        ignored ->
                                Integer.parseInt(token().substring(token().lastIndexOf('-') + 1)));
        when(admins.findById(anyInt()))
                .thenAnswer(
                        invocation -> {
                            AdminUser user = new AdminUser();
                            user.setId(invocation.getArgument(0));
                            user.setIsBanLogin(0);
                            return user;
                        });
        when(backendBus.adminUserPermissions(anyInt()))
                .thenAnswer(
                        invocation -> {
                            HashMap<String, Boolean> permissions = new HashMap<>();
                            if (!Integer.valueOf(3).equals(invocation.getArgument(0)))
                                for (String slug :
                                        List.of(
                                                "question-bank-view",
                                                "question-bank-edit",
                                                "question-bank-export",
                                                "exam-paper-view",
                                                "exam-paper-edit",
                                                "exam-paper-publish",
                                                "exam-paper-export")) permissions.put(slug, true);
                            return permissions;
                        });
        when(backendBus.isSuperAdmin())
                .thenAnswer(ignored -> Integer.valueOf(1).equals(BCtx.getId()));
        bankId = ok(QUESTION + "/banks/save", bank(), "admin-1").get("id").asLong();
        single = ok(QUESTION + "/questions/save", question("single_choice", "Q_SINGLE"), "admin-1");
        shortAnswer =
                ok(QUESTION + "/questions/save", question("short_answer", "Q_SHORT"), "admin-1");
    }

    @AfterEach
    void tearDown() {
        resetTables();
    }

    private void resetTables() {
        for (String table :
                List.of(
                        "exam_paper_versions",
                        "exam_paper_draft_items",
                        "exam_paper_draft_sections",
                        "exam_papers",
                        "exam_paper_categories",
                        "exam_paper_audit",
                        "exam_practice_attempts",
                        "exam_question_versions",
                        "exam_questions",
                        "exam_question_categories",
                        "exam_question_banks",
                        "exam_bank_audit")) db.update("DELETE FROM " + table);
    }

    private ObjectNode bank() {
        return json.createObjectNode()
                .put("name", "组卷题库")
                .put("description", "试卷接口验收")
                .put("status", "enabled")
                .put("practiceEnabled", false);
    }

    private ObjectNode question(String type, String code) {
        ObjectNode q =
                json.createObjectNode()
                        .put("bankId", bankId)
                        .put("code", code)
                        .put("type", type)
                        .put("difficulty", "medium")
                        .put("stem", code + " 的原始题干")
                        .put("suggestedScore", 5.25)
                        .put("analysis", "标准解析")
                        .put("status", "enabled");
        q.putArray("tags").add("Java");
        var options = q.putArray("options");
        ObjectNode answer = q.putObject("standardAnswer");
        if (type.equals("single_choice")) {
            options.addObject().put("id", "A").put("text", "正确选项");
            options.addObject().put("id", "B").put("text", "错误选项");
            answer.putArray("optionIds").add("A");
        } else {
            answer.put("text", "参考答案及评分要点");
        }
        q.putObject("gradingRule")
                .put("strategy", type.equals("short_answer") ? "manual" : "exact_match");
        return q;
    }

    private ObjectNode paper(Long id, Integer revision, JsonNode first, JsonNode second) {
        ObjectNode p =
                json.createObjectNode()
                        .put("code", "PAPER_001")
                        .put("name", "Java 入职考试")
                        .put("description", "固定组卷验收");
        if (id != null) p.put("id", id);
        if (revision != null) p.put("revision", revision);
        p.putArray("tags").add("入职");
        var sections = p.putArray("sections");
        var objective =
                sections.addObject()
                        .put("title", "一、单选题")
                        .put("description", "请选择唯一正确答案")
                        .put("position", 0)
                        .put("shuffleQuestions", true);
        objective
                .putArray("items")
                .addObject()
                .put("questionId", first.get("id").asLong())
                .put("questionVersion", first.get("version").asInt())
                .put("score", 5.25)
                .put("position", 0);
        var subjective =
                sections.addObject()
                        .put("title", "二、简答题")
                        .put("description", "按要点作答")
                        .put("position", 1)
                        .put("shuffleQuestions", false);
        subjective
                .putArray("items")
                .addObject()
                .put("questionId", second.get("id").asLong())
                .put("questionVersion", second.get("version").asInt())
                .put("score", 7.50)
                .put("position", 0);
        return p;
    }

    private ObjectNode query() {
        return json.createObjectNode().put("page", 1).put("size", 20);
    }

    private JsonNode post(String path, Object input, String token) {
        return exchange(path, input, token, HttpMethod.POST).getBody();
    }

    private ResponseEntity<JsonNode> exchange(
            String path, Object input, String token, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(input, headers), JsonNode.class);
    }

    private JsonNode ok(String path, Object input, String token) {
        JsonNode result = post(path, input, token);
        assertThat(result.get("code").asInt()).as(result.toString()).isZero();
        return result.get("data");
    }

    private JsonNode savePaper() {
        return ok(PAPER + "/papers/save", paper(null, null, single, shortAnswer), "admin-1");
    }

    @Test
    void everyPaperEndpointIsPostOnly() {
        List<String> paths = new ArrayList<>();
        mappings.getHandlerMethods()
                .forEach(
                        (info, handler) -> {
                            if (handler.getBeanType().equals(ExamPaperController.class)) {
                                assertThat(info.getMethodsCondition().getMethods())
                                        .containsExactly(RequestMethod.POST);
                                paths.addAll(info.getPatternValues());
                            }
                        });
        assertThat(paths).hasSize(16);
        for (String path : paths)
            assertThat(exchange(path, Map.of(), "admin-1", HttpMethod.GET).getStatusCode().value())
                    .as(path)
                    .isEqualTo(405);
    }

    @Test
    void authenticationPermissionsAndOwnershipAreEnforced() {
        assertThat(
                        exchange(PAPER + "/papers/list", query(), null, HttpMethod.POST)
                                .getStatusCode()
                                .value())
                .isEqualTo(401);
        assertThat(post(PAPER + "/papers/list", query(), "admin-3").get("code").asInt())
                .isEqualTo(403);
        JsonNode created = savePaper();
        assertThat(ok(PAPER + "/papers/list", query(), "admin-2").get("items")).isEmpty();
        assertThat(
                        post(
                                        PAPER + "/papers/save",
                                        paper(null, null, single, shortAnswer),
                                        "admin-2")
                                .get("code")
                                .asInt())
                .isNotZero();
        assertThat(
                        post(
                                        PAPER + "/papers/detail",
                                        Map.of("id", created.get("id").asLong()),
                                        "admin-2")
                                .get("code")
                                .asInt())
                .isNotZero();
    }

    @Test
    void categoriesAreIndependentScopedAndLimitedToThreeLevels() {
        long parent = 0;
        for (int i = 0; i < 3; i++)
            parent =
                    ok(
                                    PAPER + "/categories/create",
                                    Map.of("parentId", parent, "name", "分类" + i),
                                    "admin-1")
                            .asLong();
        assertThat(
                        post(
                                        PAPER + "/categories/create",
                                        Map.of("parentId", parent, "name", "第四级"),
                                        "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
        assertThat(ok(PAPER + "/categories/list", Map.of(), "admin-2")).isEmpty();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_paper_categories", Long.class))
                .isEqualTo(3);
    }

    @Test
    void fixedPaperSavesSectionsQuestionsScoresAndSearches() {
        JsonNode created = savePaper();
        assertThat(created.get("status").asText()).isEqualTo("draft");
        assertThat(created.get("questionCount").asInt()).isEqualTo(2);
        assertThat(created.get("totalScore").decimalValue()).isEqualByComparingTo("12.75");
        assertThat(created.get("objectiveScore").decimalValue()).isEqualByComparingTo("5.25");
        assertThat(created.get("subjectiveScore").decimalValue()).isEqualByComparingTo("7.50");
        assertThat(created.get("requiresManualGrading").asBoolean()).isTrue();
        assertThat(created.at("/sections/0/items/0/question/standardAnswer/optionIds/0").asText())
                .isEqualTo("A");
        JsonNode list = ok(PAPER + "/papers/list", query().put("keyword", "PAPER_001"), "admin-1");
        assertThat(list.get("total").asInt()).isEqualTo(1);
        assertThat(
                        ok(
                                        PAPER + "/questions/search",
                                        json.createObjectNode()
                                                .put("bankId", bankId)
                                                .put("page", 1)
                                                .put("size", 20),
                                        "admin-1")
                                .get("total")
                                .asInt())
                .isEqualTo(2);
    }

    @Test
    void invalidPaperRollsBackWithoutPartialRows() {
        ObjectNode duplicate = paper(null, null, single, shortAnswer);
        ((ObjectNode) duplicate.at("/sections/1/items/0"))
                .put("questionId", single.get("id").asLong())
                .put("questionVersion", single.get("version").asInt());
        assertThat(post(PAPER + "/papers/save", duplicate, "admin-1").get("code").asInt())
                .isNotZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_papers", Long.class)).isZero();
        ObjectNode missing = paper(null, null, single, shortAnswer);
        ((ObjectNode) missing.at("/sections/0/items/0")).put("questionId", 999999);
        assertThat(post(PAPER + "/papers/save", missing, "admin-1").get("code").asInt())
                .isNotZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_paper_draft_sections", Long.class))
                .isZero();
    }

    @Test
    void publishValidatesQuestionStateAndOptimisticRevision() {
        JsonNode created = savePaper();
        long id = created.get("id").asLong();
        assertThat(
                        ok(PAPER + "/papers/validate", Map.of("id", id), "admin-1")
                                .get("valid")
                                .asBoolean())
                .isTrue();
        JsonNode published =
                ok(PAPER + "/papers/publish", Map.of("id", id, "expectedRevision", 1), "admin-1");
        assertThat(published.get("version").asInt()).isEqualTo(1);
        assertThat(published.get("status").asText()).isEqualTo("published");
        assertThat(
                        post(
                                        PAPER + "/papers/publish",
                                        Map.of("id", id, "expectedRevision", 1),
                                        "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_paper_versions", Long.class))
                .isEqualTo(1);
    }

    @Test
    void emptyDraftCanBeSavedButCannotBePublished() {
        ObjectNode empty = paper(null, null, single, shortAnswer);
        empty.putArray("sections");
        JsonNode created = ok(PAPER + "/papers/save", empty, "admin-1");
        JsonNode validation =
                ok(PAPER + "/papers/validate", Map.of("id", created.get("id").asLong()), "admin-1");
        assertThat(validation.get("valid").asBoolean()).isFalse();
        assertThat(validation.get("errors").toString()).contains("至少需要一个大题");
        assertThat(
                        post(
                                        PAPER + "/papers/publish",
                                        Map.of(
                                                "id",
                                                created.get("id").asLong(),
                                                "expectedRevision",
                                                1),
                                        "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
    }

    @Test
    void batchImportIsAtomic() {
        ObjectNode first = paper(null, null, single, shortAnswer);
        first.put("code", "IMPORT_1");
        ObjectNode duplicate = paper(null, null, single, shortAnswer);
        duplicate.put("code", "IMPORT_1");
        ObjectNode payload = json.createObjectNode();
        payload.putArray("papers").add(first).add(duplicate);
        assertThat(post(PAPER + "/papers/import", payload, "admin-1").get("code").asInt())
                .isNotZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_papers", Long.class)).isZero();

        duplicate.put("code", "IMPORT_2");
        JsonNode result = ok(PAPER + "/papers/import", payload, "admin-1");
        assertThat(result.get("count").asInt()).isEqualTo(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_papers", Long.class)).isEqualTo(2);
    }

    @Test
    void publishedVersionsRemainImmutableWhenQuestionsAndDraftChange() {
        JsonNode created = savePaper();
        long id = created.get("id").asLong();
        ok(PAPER + "/papers/publish", Map.of("id", id, "expectedRevision", 1), "admin-1");

        ObjectNode changedQuestion = question("single_choice", "Q_SINGLE");
        changedQuestion
                .put("id", single.get("id").asLong())
                .put("expectedVersion", single.get("version").asInt())
                .put("stem", "修改后的题干");
        JsonNode latestQuestion = ok(QUESTION + "/questions/save", changedQuestion, "admin-1");
        JsonNode staleDraft =
                ok(PAPER + "/papers/save", paper(id, 2, single, shortAnswer), "admin-1");
        JsonNode validation = ok(PAPER + "/papers/validate", Map.of("id", id), "admin-1");
        assertThat(validation.get("valid").asBoolean()).isFalse();
        assertThat(validation.get("errors").toString()).contains("已有新版本");

        JsonNode freshDraft =
                ok(
                        PAPER + "/papers/save",
                        paper(id, staleDraft.get("revision").asInt(), latestQuestion, shortAnswer),
                        "admin-1");
        ok(
                PAPER + "/papers/publish",
                Map.of("id", id, "expectedRevision", freshDraft.get("revision").asInt()),
                "admin-1");
        JsonNode version1 = ok(PAPER + "/papers/detail", Map.of("id", id, "version", 1), "admin-1");
        JsonNode version2 = ok(PAPER + "/papers/detail", Map.of("id", id, "version", 2), "admin-1");
        assertThat(version1.at("/sections/0/items/0/question/stem").asText()).contains("原始题干");
        assertThat(version2.at("/sections/0/items/0/question/stem").asText()).isEqualTo("修改后的题干");
        assertThat(ok(PAPER + "/papers/versions", Map.of("id", id), "admin-1")).hasSize(2);
    }

    @Test
    void disabledQuestionsCannotBePublished() {
        JsonNode created = savePaper();
        ok(
                QUESTION + "/questions/status",
                Map.of(
                        "id",
                        single.get("id").asLong(),
                        "expectedVersion",
                        single.get("version").asInt(),
                        "status",
                        "disabled"),
                "admin-1");
        JsonNode validation =
                ok(PAPER + "/papers/validate", Map.of("id", created.get("id").asLong()), "admin-1");
        assertThat(validation.get("valid").asBoolean()).isFalse();
        assertThat(validation.get("errors").toString()).contains("未启用");
    }

    @Test
    void copyExportDeleteAndLifecycleRulesAreEnforced() {
        JsonNode created = savePaper();
        long id = created.get("id").asLong();
        assertThat(post(PAPER + "/papers/export", Map.of("id", id), "admin-1").get("code").asInt())
                .isNotZero();
        JsonNode published =
                ok(PAPER + "/papers/publish", Map.of("id", id, "expectedRevision", 1), "admin-1");
        JsonNode exported = ok(PAPER + "/papers/export", Map.of("id", id), "admin-1");
        assertThat(exported.at("/sections/0/items/0/question/standardAnswer").isObject()).isTrue();
        JsonNode copied =
                ok(
                        PAPER + "/papers/copy",
                        Map.of("sourceId", id, "version", 1, "code", "PAPER_COPY", "name", "复制试卷"),
                        "admin-1");
        assertThat(copied.get("status").asText()).isEqualTo("draft");
        assertThat(copied.get("currentVersion").asInt()).isZero();
        ok(PAPER + "/papers/delete-draft", Map.of("id", copied.get("id").asLong()), "admin-1");
        JsonNode disabled =
                ok(
                        PAPER + "/papers/status",
                        Map.of("id", id, "expectedRevision", 2, "status", "disabled"),
                        "admin-1");
        JsonNode archived =
                ok(
                        PAPER + "/papers/status",
                        Map.of(
                                "id",
                                id,
                                "expectedRevision",
                                disabled.get("revision").asInt(),
                                "status",
                                "archived"),
                        "admin-1");
        assertThat(archived.get("status").asText()).isEqualTo("archived");
        assertThat(
                        post(
                                        PAPER + "/papers/save",
                                        paper(
                                                id,
                                                archived.get("revision").asInt(),
                                                single,
                                                shortAnswer),
                                        "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
        assertThat(
                        post(PAPER + "/papers/delete-draft", Map.of("id", id), "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
        assertThat(published.get("totalScore").decimalValue()).isEqualByComparingTo("12.75");
    }

    @Test
    void migrationIsRestartableAndCreatesAllTablesOnMysql() {
        savePaper();
        migration.run();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_papers", Long.class)).isEqualTo(1);
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM information_schema.TABLES WHERE"
                                        + " TABLE_SCHEMA=? AND TABLE_NAME LIKE 'exam_paper%'",
                                Long.class, MYSQL_DATABASE))
                .isEqualTo(6);
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM migrations WHERE"
                                        + " migration='20260907_exam_paper_v1'",
                                Long.class))
                .isEqualTo(1);
    }
}
