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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import xyz.playedu.api.controller.backend.QuestionBankController;
import xyz.playedu.api.controller.frontend.QuestionPracticeController;
import xyz.playedu.api.interceptor.*;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.config.PlayEduConfig;
import xyz.playedu.common.context.BCtx;
import xyz.playedu.common.domain.AdminUser;
import xyz.playedu.common.domain.User;
import xyz.playedu.common.service.*;
import xyz.playedu.exam.*;
import xyz.playedu.system.aspectj.BackendPermissionAspect;

/**
 * Real HTTP + real SQL + real validation/interceptors/permissions; only existing account services
 * are mocked.
 */
@SpringBootTest(
        classes = QuestionBankHttpTest.Harness.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=test",
            "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
        })
class QuestionBankHttpTest {
    private static final String ADMIN = "/backend/v1/question-bank";
    private static final String STUDENT = "/api/v1/question-bank";
    private static final String MYSQL_HOST = env("QUESTION_BANK_TEST_DB_HOST", "127.0.0.1");
    private static final String MYSQL_PORT = env("QUESTION_BANK_TEST_DB_PORT", "23307");
    private static final String MYSQL_DATABASE =
            env("QUESTION_BANK_TEST_DB_NAME", "playedu_question_bank_test");
    private static final String MYSQL_USER = env("QUESTION_BANK_TEST_DB_USER", "root");
    private static final String MYSQL_PASSWORD = env("QUESTION_BANK_TEST_DB_PASS", "playeduxyz");

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) throws Exception {
        if (!MYSQL_DATABASE.matches("[a-zA-Z0-9_]+_test")) {
            throw new IllegalStateException("QUESTION_BANK_TEST_DB_NAME 必须以 _test 结尾，拒绝连接非测试库");
        }
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
        if (!MYSQL_DATABASE.matches("[a-zA-Z0-9_]+_test")) {
            throw new IllegalStateException("拒绝删除非测试库");
        }
        String serverUrl =
                "jdbc:mysql://"
                        + MYSQL_HOST
                        + ":"
                        + MYSQL_PORT
                        + "/?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
        try (var connection = DriverManager.getConnection(serverUrl, MYSQL_USER, MYSQL_PASSWORD);
                var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + MYSQL_DATABASE + "`");
            try (var result =
                    statement.executeQuery(
                            "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"
                                    + MYSQL_DATABASE
                                    + "'")) {
                result.next();
                if (result.getInt(1) != 0) {
                    throw new IllegalStateException("MySQL 临时测试库删除失败");
                }
            }
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @Import({
        QuestionBankController.class,
        QuestionPracticeController.class,
        QuestionBankService.class,
        QuestionGrader.class,
        QuestionBankMigration.class,
        PracticeAutoGradingMigration.class,
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
    @Autowired QuestionBankMigration migration;
    @MockBean BackendAuthService adminAuth;
    @MockBean FrontendAuthService studentAuth;
    @MockBean AdminUserService admins;
    @MockBean UserService users;
    @MockBean BackendBus backendBus;
    @MockBean AppConfigService config;
    @MockBean RateLimiterService limiter;

    private long bankId;

    private String token() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest()
                .getHeader("Authorization");
    }

    @BeforeEach
    void setUp() throws Exception {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        resetQuestionBankTables();
        when(config.keyValues()).thenReturn(Map.of());
        when(limiter.current(anyString(), anyLong())).thenReturn(0L);
        when(adminAuth.check())
                .thenAnswer(
                        inv ->
                                Set.of("Bearer admin-1", "Bearer admin-2", "Bearer admin-3")
                                        .contains(Objects.toString(token(), "")));
        when(adminAuth.userId())
                .thenAnswer(
                        inv -> Integer.parseInt(token().substring(token().lastIndexOf('-') + 1)));
        when(admins.findById(anyInt()))
                .thenAnswer(
                        inv -> {
                            AdminUser a = new AdminUser();
                            a.setId(inv.getArgument(0));
                            a.setIsBanLogin(0);
                            return a;
                        });
        when(backendBus.adminUserPermissions(anyInt()))
                .thenAnswer(
                        inv -> {
                            HashMap<String, Boolean> p = new HashMap<>();
                            if (!Integer.valueOf(3).equals(inv.getArgument(0)))
                                for (String slug :
                                        List.of(
                                                "question-bank-view",
                                                "question-bank-edit",
                                                "question-bank-export")) p.put(slug, true);
                            return p;
                        });
        when(backendBus.isSuperAdmin()).thenAnswer(inv -> Integer.valueOf(1).equals(BCtx.getId()));
        when(studentAuth.check())
                .thenAnswer(
                        inv ->
                                Set.of("Bearer student-10", "Bearer student-11")
                                        .contains(Objects.toString(token(), "")));
        when(studentAuth.userId())
                .thenAnswer(
                        inv -> Integer.parseInt(token().substring(token().lastIndexOf('-') + 1)));
        when(studentAuth.jti()).thenReturn("test-session");
        when(users.find(anyInt()))
                .thenAnswer(
                        inv -> {
                            User user = new User();
                            user.setId(inv.getArgument(0));
                            user.setIsLock(0);
                            return user;
                        });
        bankId = ok(ADMIN + "/banks/save", bank(null, false), "admin-1").get("id").asLong();
    }

    @AfterEach
    void tearDown() {
        resetQuestionBankTables();
    }

    private void resetQuestionBankTables() {
        for (String table :
                List.of(
                        "exam_practice_paper_attempts",
                        "exam_practice_attempts",
                        "exam_question_versions",
                        "exam_questions",
                        "exam_question_categories",
                        "exam_question_banks",
                        "exam_bank_audit")) db.update("DELETE FROM " + table);
    }

    private ObjectNode bank(Long id, boolean practice) {
        ObjectNode input =
                json.createObjectNode()
                        .put("name", "数据库题库")
                        .put("description", "HTTP 验收")
                        .put("status", "enabled")
                        .put("practiceEnabled", practice);
        if (id != null) input.put("id", id).put("revision", 1);
        return input;
    }

    private ObjectNode question(String type, String code) {
        ObjectNode q =
                json.createObjectNode()
                        .put("bankId", bankId)
                        .put("code", code)
                        .put("type", type)
                        .put("difficulty", "medium")
                        .put("stem", "请选择正确答案")
                        .put("suggestedScore", 5.25)
                        .put("analysis", "仅在交卷后显示的解析")
                        .put("status", "enabled");
        q.putArray("tags").add("数据库");
        var options = q.putArray("options");
        ObjectNode answer = q.putObject("standardAnswer");
        if (type.endsWith("choice")) {
            for (int i = 1; i <= 3; i++)
                options.addObject().put("id", "opt_" + i).put("text", "选项" + i);
            var ids = answer.putArray("optionIds").add("opt_1");
            if (type.equals("multiple_choice")) ids.add("opt_3");
        } else if (type.equals("true_false")) answer.put("value", false);
        else answer.put("text", "参考答案；评分要点：概念 2 分，示例 3.25 分");
        q.putObject("gradingRule")
                .put("strategy", type.equals("short_answer") ? "manual" : "exact_match");
        return q;
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

    private ObjectNode query() {
        return json.createObjectNode().put("bankId", bankId).put("page", 1).put("size", 20);
    }

    private ObjectNode submission(JsonNode q, Object answer, String key) {
        ObjectNode result =
                json.createObjectNode()
                        .put("questionId", q.get("id").asLong())
                        .put("version", q.get("version").asInt())
                        .put("requestKey", key);
        result.set("answer", json.valueToTree(answer));
        return result;
    }

    private void openPractice() {
        ok(ADMIN + "/banks/save", bank(bankId, true), "admin-1");
    }

    @Test
    void everyEndpointIsPostOnly() {
        List<String> paths = new ArrayList<>();
        mappings.getHandlerMethods()
                .forEach(
                        (info, handler) -> {
                            if (handler.getBeanType().equals(QuestionBankController.class)
                                    || handler.getBeanType()
                                            .equals(QuestionPracticeController.class)) {
                                assertThat(info.getMethodsCondition().getMethods())
                                        .containsExactly(RequestMethod.POST);
                                paths.addAll(info.getPatternValues());
                            }
                        });
        assertThat(paths).hasSize(22);
        for (String path : paths) {
            ResponseEntity<JsonNode> response = exchange(path, Map.of(), "admin-1", HttpMethod.GET);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(405);
        }
        for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH))
            assertThat(
                            exchange(ADMIN + "/banks/list", Map.of(), "admin-1", method)
                                    .getStatusCode()
                                    .value())
                    .isEqualTo(405);
    }

    @Test
    void authenticationPermissionsAndOwnershipAreEnforced() {
        assertThat(
                        exchange(ADMIN + "/banks/list", Map.of(), null, HttpMethod.POST)
                                .getStatusCode()
                                .value())
                .isEqualTo(401);
        assertThat(
                        exchange(STUDENT + "/banks/list", Map.of(), "admin-1", HttpMethod.POST)
                                .getStatusCode()
                                .value())
                .isEqualTo(401);
        assertThat(post(ADMIN + "/banks/list", Map.of(), "admin-3").get("code").asInt())
                .isEqualTo(403);
        assertThat(ok(ADMIN + "/banks/list", Map.of(), "admin-2")).isEmpty();
        assertThat(post(ADMIN + "/questions/list", query(), "admin-2").get("code").asInt())
                .isNotZero();
        assertThat(post(ADMIN + "/banks/save", bank(bankId, true), "admin-2").get("code").asInt())
                .isNotZero();
    }

    @Test
    void bankCrudAndOptimisticRevision() {
        assertThat(ok(ADMIN + "/banks/list", Map.of(), "admin-1")).hasSize(1);
        assertThat(ok(ADMIN + "/banks/save", bank(bankId, true), "admin-1").get("revision").asInt())
                .isEqualTo(2);
        assertThat(post(ADMIN + "/banks/save", bank(bankId, false), "admin-1").get("code").asInt())
                .isNotZero();
        ok(ADMIN + "/banks/delete", Map.of("id", bankId), "admin-1");
        assertThat(ok(ADMIN + "/banks/list", Map.of(), "admin-1")).isEmpty();
    }

    @Test
    void categoriesAreScopedAndLimitedToThreeLevels() {
        long parent = 0;
        for (int i = 0; i < 3; i++)
            parent =
                    ok(
                                    ADMIN + "/categories/create",
                                    Map.of("bankId", bankId, "parentId", parent, "name", "层级" + i),
                                    "admin-1")
                            .asLong();
        assertThat(
                        post(
                                        ADMIN + "/categories/create",
                                        Map.of("bankId", bankId, "parentId", parent, "name", "四级"),
                                        "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
        assertThat(ok(ADMIN + "/categories/list", Map.of("bankId", bankId), "admin-1")).hasSize(3);
        ObjectNode q = question("single_choice", "CAT").put("categoryId", parent);
        JsonNode saved = ok(ADMIN + "/questions/save", q, "admin-1");
        assertThat(
                        post(ADMIN + "/categories/delete", Map.of("id", parent), "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
        long otherBank = ok(ADMIN + "/banks/save", bank(null, false), "admin-1").get("id").asLong();
        q.put("bankId", otherBank);
        assertThat(post(ADMIN + "/questions/save", q, "admin-1").get("code").asInt()).isNotZero();
        assertThat(saved.get("categoryId").asLong()).isEqualTo(parent);
    }

    @ParameterizedTest
    @ValueSource(strings = {"single_choice", "multiple_choice", "true_false", "short_answer"})
    void createsAndReadsStructuredQuestions(String type) {
        JsonNode saved = ok(ADMIN + "/questions/save", question(type, "CREATE"), "admin-1");
        JsonNode detail =
                ok(ADMIN + "/questions/detail", Map.of("id", saved.get("id").asLong()), "admin-1");
        assertThat(detail.get("type").asText()).isEqualTo(type);
        assertThat(detail.get("standardAnswer")).isEqualTo(saved.get("standardAnswer"));
        assertThat(detail.get("suggestedScore").decimalValue()).isEqualByComparingTo("5.25");
        assertThat(
                        post(ADMIN + "/banks/delete", Map.of("id", bankId), "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
    }

    @Test
    void invalidContentIsRejectedWithoutWrites() {
        ObjectNode q = question("single_choice", "BAD");
        ((ObjectNode) q.get("standardAnswer")).putArray("optionIds").add("unknown");
        assertThat(post(ADMIN + "/questions/save", q, "admin-1").get("code").asInt()).isNotZero();
        q = question("multiple_choice", "BAD");
        q.withArray("options").add(q.get("options").get(0));
        assertThat(post(ADMIN + "/questions/save", q, "admin-1").get("code").asInt()).isNotZero();
        q = question("true_false", "BAD");
        q.put("suggestedScore", -1);
        assertThat(post(ADMIN + "/questions/save", q, "admin-1").get("code").asInt())
                .isEqualTo(406);
        q = question("single_choice", "BAD");
        q.putObject("gradingRule").put("strategy", "manual");
        assertThat(post(ADMIN + "/questions/save", q, "admin-1").get("code").asInt()).isNotZero();
        assertThat(ok(ADMIN + "/questions/list", query(), "admin-1").get("total").asInt()).isZero();
    }

    @Test
    void versionsStayImmutableAndStaleUpdatesFail() {
        ObjectNode q = question("single_choice", "VERSION");
        long id = ok(ADMIN + "/questions/save", q, "admin-1").get("id").asLong();
        q.put("id", id).put("expectedVersion", 1).put("stem", "新版题干");
        ok(ADMIN + "/questions/save", q, "admin-1");
        assertThat(post(ADMIN + "/questions/save", q, "admin-1").get("code").asInt()).isNotZero();
        assertThat(
                        ok(ADMIN + "/questions/detail", Map.of("id", id, "version", 1), "admin-1")
                                .get("stem")
                                .asText())
                .isEqualTo("请选择正确答案");
        assertThat(ok(ADMIN + "/questions/versions", Map.of("id", id), "admin-1")).hasSize(2);
        ok(
                ADMIN + "/questions/status",
                Map.of("id", id, "expectedVersion", 2, "status", "archived"),
                "admin-1");
        assertThat(
                        post(
                                        ADMIN + "/questions/status",
                                        Map.of("id", id, "expectedVersion", 3, "status", "enabled"),
                                        "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
    }

    @Test
    void importIsAtomicAndDuplicateRetryDoesNotCreateExtraQuestions() {
        ObjectNode first = question("single_choice", "IMPORT1");
        ObjectNode bad = question("multiple_choice", "IMPORT2");
        bad.putObject("standardAnswer").putArray("optionIds").add("bad");
        JsonNode failure =
                post(
                        ADMIN + "/questions/import",
                        Map.of("bankId", bankId, "questions", List.of(first, bad)),
                        "admin-1");
        assertThat(failure.get("msg").asText()).contains("第 2 行");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_questions", Long.class)).isZero();
        Object input =
                Map.of(
                        "bankId",
                        bankId,
                        "questions",
                        List.of(first, question("true_false", "IMPORT2")));
        assertThat(ok(ADMIN + "/questions/import", input, "admin-1").get("count").asInt())
                .isEqualTo(2);
        assertThat(post(ADMIN + "/questions/import", input, "admin-1").get("code").asInt())
                .isNotZero();
        assertThat(ok(ADMIN + "/questions/export", Map.of("bankId", bankId), "admin-1")).hasSize(2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_questions", Long.class))
                .isEqualTo(2);
    }

    @Test
    void filtersAndPaginationWork() {
        ok(
                ADMIN + "/questions/import",
                Map.of(
                        "bankId",
                        bankId,
                        "questions",
                        List.of(question("single_choice", "F1"), question("true_false", "F2"))),
                "admin-1");
        assertThat(ok(ADMIN + "/questions/list", query().put("size", 1), "admin-1").get("items"))
                .hasSize(1);
        assertThat(
                        ok(
                                        ADMIN + "/questions/list",
                                        query().put("type", "true_false").put("tag", "数据库"),
                                        "admin-1")
                                .get("total")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        ok(
                                        ADMIN + "/questions/list",
                                        query().put("keyword", "' OR 1=1 --"),
                                        "admin-1")
                                .get("total")
                                .asInt())
                .isZero();
        assertThat(
                        post(ADMIN + "/questions/list", query().put("size", 1001), "admin-1")
                                .get("code")
                                .asInt())
                .isEqualTo(406);
    }

    @Test
    void practiceIsOptInAndReadResponsesNeverContainAnswers() {
        JsonNode q = ok(ADMIN + "/questions/save", question("single_choice", "PRIVATE"), "admin-1");
        assertThat(ok(STUDENT + "/banks/list", Map.of(), "student-10")).isEmpty();
        assertThat(
                        post(
                                        STUDENT + "/questions/detail",
                                        Map.of("id", q.get("id").asLong()),
                                        "student-10")
                                .get("code")
                                .asInt())
                .isNotZero();
        openPractice();
        ok(ADMIN + "/questions/save", question("short_answer", "MANUAL"), "admin-1");
        assertThat(ok(STUDENT + "/banks/list", Map.of(), "student-10")).hasSize(1);
        JsonNode list = ok(STUDENT + "/questions/list", query(), "student-10");
        assertThat(list.get("total").asInt()).isEqualTo(1);
        for (JsonNode node :
                List.of(
                        list,
                        ok(
                                STUDENT + "/questions/detail",
                                Map.of("id", q.get("id").asLong()),
                                "student-10")))
            assertThat(node.toString())
                    .doesNotContain("standardAnswer", "analysis", "gradingRule", "仅在交卷后显示");
    }

    @Test
    void gradingUsesSetsAndDecimalScoresAndRejectsInvalidOptions() {
        openPractice();
        JsonNode q = ok(ADMIN + "/questions/save", question("multiple_choice", "GRADE"), "admin-1");
        JsonNode correct =
                ok(
                        STUDENT + "/practice/submit",
                        submission(q, Map.of("optionIds", List.of("opt_3", "opt_1")), "grade-0001"),
                        "student-10");
        assertThat(correct.get("score").decimalValue()).isEqualByComparingTo("5.25");
        for (List<String> answer :
                List.of(List.of("opt_1"), List.of("opt_1", "opt_2", "opt_3"), List.<String>of())) {
            JsonNode wrong =
                    ok(
                            STUDENT + "/practice/submit",
                            submission(
                                    q, Map.of("optionIds", answer), UUID.randomUUID().toString()),
                            "student-10");
            assertThat(wrong.get("score").decimalValue()).isEqualByComparingTo("0");
        }
        for (List<String> answer : List.of(List.of("unknown"), List.of("opt_1", "opt_1")))
            assertThat(
                            post(
                                            STUDENT + "/practice/submit",
                                            submission(
                                                    q,
                                                    Map.of("optionIds", answer),
                                                    UUID.randomUUID().toString()),
                                            "student-10")
                                    .get("code")
                                    .asInt())
                    .isNotZero();
    }

    @Test
    void falseIsAValidAnswerAndUnansweredIsZero() {
        openPractice();
        JsonNode q = ok(ADMIN + "/questions/save", question("true_false", "FALSE"), "admin-1");
        assertThat(
                        ok(
                                        STUDENT + "/practice/submit",
                                        submission(q, Map.of("value", false), "false-0001"),
                                        "student-10")
                                .get("result")
                                .asText())
                .isEqualTo("correct");
        assertThat(
                        ok(
                                        STUDENT + "/practice/submit",
                                        submission(q, Map.of(), "false-0002"),
                                        "student-10")
                                .get("score")
                                .asDouble())
                .isZero();
    }

    @Test
    void practiceSubmissionIsIdempotentAndHistoryIsPrivate() {
        openPractice();
        JsonNode q = ok(ADMIN + "/questions/save", question("single_choice", "RETRY"), "admin-1");
        ObjectNode request = submission(q, Map.of("optionIds", List.of("opt_1")), "retry-0001");
        JsonNode first = ok(STUDENT + "/practice/submit", request, "student-10");
        assertThat(ok(STUDENT + "/practice/submit", request, "student-10")).isEqualTo(first);
        request.putObject("answer").putArray("optionIds").add("opt_2");
        assertThat(post(STUDENT + "/practice/submit", request, "student-10").get("code").asInt())
                .isNotZero();
        assertThat(ok(STUDENT + "/practice/history", query(), "student-10").get("total").asInt())
                .isEqualTo(1);
        assertThat(ok(STUDENT + "/practice/history", query(), "student-11").get("total").asInt())
                .isZero();
    }

    @Test
    void paperSubmissionAutomaticallyGradesEveryQuestionAndIsIdempotent() {
        openPractice();
        JsonNode first =
                ok(ADMIN + "/questions/save", question("single_choice", "PAPER_ONE"), "admin-1");
        ok(ADMIN + "/questions/save", question("true_false", "PAPER_TWO"), "admin-1");
        ObjectNode request =
                json.createObjectNode().put("bankId", bankId).put("requestKey", "paper-0001");
        request
                .putArray("answers")
                .addObject()
                .put("questionId", first.get("id").asLong())
                .put("version", first.get("version").asInt())
                .putObject("answer")
                .putArray("optionIds")
                .add("opt_1");

        JsonNode result = ok(STUDENT + "/practice/paper/submit", request, "student-10");
        assertThat(result.get("score").decimalValue()).isEqualByComparingTo("5.25");
        assertThat(result.get("maxScore").decimalValue()).isEqualByComparingTo("10.50");
        assertThat(result.get("questionCount").asInt()).isEqualTo(2);
        assertThat(result.get("correctCount").asInt()).isEqualTo(1);
        assertThat(result.get("items")).hasSize(2);
        assertThat(result.toString()).contains("standardAnswer", "analysis");
        assertThat(ok(STUDENT + "/practice/paper/submit", request, "student-10"))
                .isEqualTo(result);
        assertThat(
                        ok(STUDENT + "/practice/paper/history", query(), "student-10")
                                .get("total")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        ok(STUDENT + "/practice/paper/history", query(), "student-11")
                                .get("total")
                                .asInt())
                .isZero();
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM exam_practice_paper_attempts", Long.class))
                .isEqualTo(1);
    }

    @Test
    void stalePracticeVersionAndDisabledQuestionsAreRejected() {
        openPractice();
        ObjectNode input = question("single_choice", "STALE");
        JsonNode q = ok(ADMIN + "/questions/save", input, "admin-1");
        input.put("id", q.get("id").asLong()).put("expectedVersion", 1).put("stem", "新题干");
        ok(ADMIN + "/questions/save", input, "admin-1");
        assertThat(
                        post(
                                        STUDENT + "/practice/submit",
                                        submission(
                                                q,
                                                Map.of("optionIds", List.of("opt_1")),
                                                "stale-0001"),
                                        "student-10")
                                .get("code")
                                .asInt())
                .isNotZero();
        ok(
                ADMIN + "/questions/status",
                Map.of("id", q.get("id").asLong(), "expectedVersion", 2, "status", "disabled"),
                "admin-1");
        assertThat(
                        post(
                                        STUDENT + "/questions/detail",
                                        Map.of("id", q.get("id").asLong()),
                                        "student-10")
                                .get("code")
                                .asInt())
                .isNotZero();
    }

    @Test
    void migrationCanRunAgainWithoutLosingData() {
        migration.run();
        assertThat(ok(ADMIN + "/banks/list", Map.of(), "admin-1")).hasSize(1);
        assertThat(
                        db.queryForObject(
                                "SELECT COUNT(*) FROM migrations WHERE"
                                        + " migration='20260905_question_bank_v1'",
                                Long.class))
                .isEqualTo(1);
    }

    @Test
    void canRenameCategoriesAndDeleteOnlyInitialDrafts() {
        long category =
                ok(
                                ADMIN + "/categories/create",
                                Map.of("bankId", bankId, "parentId", 0, "name", "原分类"),
                                "admin-1")
                        .asLong();
        ok(ADMIN + "/categories/rename", Map.of("id", category, "name", "新分类"), "admin-1");
        assertThat(
                        ok(ADMIN + "/categories/list", Map.of("bankId", bankId), "admin-1")
                                .get(0)
                                .get("name")
                                .asText())
                .isEqualTo("新分类");
        ObjectNode input = question("single_choice", "DRAFT").put("status", "draft");
        long id = ok(ADMIN + "/questions/save", input, "admin-1").get("id").asLong();
        ok(ADMIN + "/questions/delete-draft", Map.of("id", id), "admin-1");
        ok(ADMIN + "/categories/delete", Map.of("id", category), "admin-1");
        assertThat(ok(ADMIN + "/questions/list", query(), "admin-1").get("total").asInt()).isZero();
        id =
                ok(ADMIN + "/questions/save", question("single_choice", "LIVE"), "admin-1")
                        .get("id")
                        .asLong();
        assertThat(
                        post(ADMIN + "/questions/delete-draft", Map.of("id", id), "admin-1")
                                .get("code")
                                .asInt())
                .isNotZero();
    }

    @Test
    void concurrentPracticeRetriesPersistExactlyOneResult() throws Exception {
        openPractice();
        JsonNode q =
                ok(ADMIN + "/questions/save", question("single_choice", "CONCURRENT"), "admin-1");
        ObjectNode request =
                submission(q, Map.of("optionIds", List.of("opt_1")), "concurrent-0001");
        var first =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> ok(STUDENT + "/practice/submit", request, "student-10"));
        var second =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> ok(STUDENT + "/practice/submit", request, "student-10"));
        assertThat(first.get()).isEqualTo(second.get());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM exam_practice_attempts", Long.class))
                .isEqualTo(1);
    }
}
