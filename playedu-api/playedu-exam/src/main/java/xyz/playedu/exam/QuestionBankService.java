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
package xyz.playedu.exam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.context.BCtx;
import xyz.playedu.common.exception.ServiceException;
import xyz.playedu.exam.QuestionBankTypes.*;

/** Question-bank persistence and version boundaries; all writes are transactional. */
@Service
public class QuestionBankService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final QuestionGrader grader;
    private final BackendBus backendBus;

    public QuestionBankService(
            JdbcTemplate db, ObjectMapper json, QuestionGrader grader, BackendBus backendBus) {
        this.db = db;
        this.json = json;
        this.grader = grader;
        this.backendBus = backendBus;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ServiceException(message);
    }

    private void audit(String action, long targetId) {
        db.update(
                "INSERT INTO exam_bank_audit(admin_id,action,target_id) VALUES(?,?,?)",
                BCtx.getId(),
                action,
                targetId);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        db.update(
                connection -> {
                    var statement = connection.prepareStatement(sql, new String[] {"id"});
                    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                    return statement;
                },
                key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize question", e);
        }
    }

    private QuestionInput decode(String value) {
        try {
            return json.readValue(value, QuestionInput.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored question", e);
        }
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = db.queryForList(sql, args);
        require(!rows.isEmpty(), "记录不存在或已删除");
        return rows.get(0);
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private Map<String, Object> bank(long id, boolean lock) {
        return one(
                "SELECT * FROM exam_question_banks WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    }

    private Map<String, Object> managedBank(long id, boolean lock) {
        Map<String, Object> row = bank(id, lock);
        require(
                BCtx.getId() != null
                        && (number(row, "owner_id") == BCtx.getId() || backendBus.isSuperAdmin()),
                "无权访问该题库");
        return row;
    }

    private Map<String, Object> bankView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("name", row.get("name"));
        result.put("description", row.get("description"));
        result.put("status", row.get("status"));
        result.put("practiceEnabled", row.get("practice_enabled"));
        result.put("revision", row.get("revision"));
        result.put("ownerId", row.get("owner_id"));
        result.put("createdAt", row.get("created_at"));
        result.put(
                "questionCount",
                db.queryForObject(
                        "SELECT COUNT(*) FROM exam_questions WHERE bank_id=?",
                        Long.class,
                        row.get("id")));
        return result;
    }

    public List<Map<String, Object>> banks() {
        List<Map<String, Object>> rows =
                backendBus.isSuperAdmin()
                        ? db.queryForList("SELECT * FROM exam_question_banks ORDER BY id DESC")
                        : db.queryForList(
                                "SELECT * FROM exam_question_banks WHERE owner_id=? ORDER BY id"
                                        + " DESC",
                                BCtx.getId());
        return rows.stream().map(this::bankView).toList();
    }

    @Transactional
    public Map<String, Object> saveBank(BankInput input) {
        long id;
        if (input.id() == null) {
            id =
                    insert(
                            "INSERT INTO"
                                + " exam_question_banks(name,description,status,practice_enabled,owner_id)"
                                + " VALUES(?,?,?,?,?)",
                            input.name().trim(),
                            input.description(),
                            input.status(),
                            input.practiceEnabled(),
                            BCtx.getId());
        } else {
            id = input.id();
            Map<String, Object> old = managedBank(id, true);
            require(
                    input.revision() != null && number(old, "revision") == input.revision(),
                    "题库已更新，请刷新后重试");
            db.update(
                    "UPDATE exam_question_banks SET"
                        + " name=?,description=?,status=?,practice_enabled=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=?",
                    input.name().trim(),
                    input.description(),
                    input.status(),
                    input.practiceEnabled(),
                    id);
        }
        audit("bank.save", id);
        return bankView(bank(id, false));
    }

    @Transactional
    public void deleteBank(long id) {
        managedBank(id, true);
        require(
                db.queryForObject(
                                "SELECT COUNT(*) FROM exam_questions WHERE bank_id=?",
                                Long.class,
                                id)
                        == 0,
                "题库包含题目，请停用题库以保留历史版本");
        db.update("DELETE FROM exam_question_categories WHERE bank_id=?", id);
        db.update("DELETE FROM exam_question_banks WHERE id=?", id);
        audit("bank.delete", id);
    }

    private List<Map<String, Object>> categoryRows(long bankId) {
        return db.query(
                "SELECT * FROM exam_question_categories WHERE bank_id=? ORDER BY id",
                (rs, index) ->
                        Map.<String, Object>of(
                                "id",
                                rs.getLong("id"),
                                "bankId",
                                rs.getLong("bank_id"),
                                "parentId",
                                rs.getLong("parent_id"),
                                "name",
                                rs.getString("name")),
                bankId);
    }

    public List<Map<String, Object>> categories(long bankId) {
        managedBank(bankId, false);
        return categoryRows(bankId);
    }

    @Transactional
    public long createCategory(CategoryInput input) {
        managedBank(input.bankId(), true);
        long parent = input.parentId();
        int depth = 1;
        while (parent != 0) {
            Map<String, Object> row =
                    one(
                            "SELECT * FROM exam_question_categories WHERE id=? AND bank_id=?",
                            parent,
                            input.bankId());
            parent = number(row, "parent_id");
            require(++depth <= 3, "分类最多三级");
        }
        long id =
                insert(
                        "INSERT INTO exam_question_categories(bank_id,parent_id,name)"
                                + " VALUES(?,?,?)",
                        input.bankId(),
                        input.parentId(),
                        input.name().trim());
        audit("category.create", id);
        return id;
    }

    @Transactional
    public void renameCategory(CategoryRename input) {
        Map<String, Object> row =
                one("SELECT * FROM exam_question_categories WHERE id=?", input.id());
        managedBank(number(row, "bank_id"), true);
        db.update(
                "UPDATE exam_question_categories SET name=? WHERE id=?",
                input.name().trim(),
                input.id());
        audit("category.rename", input.id());
    }

    @Transactional
    public void deleteCategory(long id) {
        Map<String, Object> row = one("SELECT * FROM exam_question_categories WHERE id=?", id);
        managedBank(number(row, "bank_id"), true);
        require(
                db.queryForObject(
                                "SELECT COUNT(*) FROM exam_question_categories WHERE parent_id=?",
                                Long.class,
                                id)
                        == 0,
                "请先删除子分类");
        require(
                db.queryForObject(
                                "SELECT COUNT(*) FROM exam_questions WHERE category_id=?",
                                Long.class,
                                id)
                        == 0,
                "分类仍有题目，无法删除");
        db.update("DELETE FROM exam_question_categories WHERE id=?", id);
        audit("category.delete", id);
    }

    private void validateCategory(QuestionInput q) {
        if (q.categoryId() != null)
            one(
                    "SELECT id FROM exam_question_categories WHERE id=? AND bank_id=?",
                    q.categoryId(),
                    q.bankId());
    }

    @Transactional
    public Map<String, Object> saveQuestion(QuestionInput input) {
        managedBank(input.bankId(), true);
        grader.validate(input);
        validateCategory(input);
        long id;
        int version = 1;
        if (input.id() == null) {
            id =
                    insert(
                            "INSERT INTO"
                                + " exam_questions(bank_id,category_id,code,type,difficulty,stem,tags_json,status)"
                                + " VALUES(?,?,?,?,?,?,?,?)",
                            input.bankId(),
                            input.categoryId(),
                            input.code(),
                            input.type(),
                            input.difficulty(),
                            input.stem(),
                            encode(input.tags()),
                            input.status());
        } else {
            id = input.id();
            Map<String, Object> row = one("SELECT * FROM exam_questions WHERE id=? FOR UPDATE", id);
            require(number(row, "bank_id") == input.bankId(), "题目不能移动到其他题库");
            require(
                    input.expectedVersion() != null
                            && number(row, "current_version") == input.expectedVersion(),
                    "题目已更新，请刷新后重试");
            require(!row.get("status").equals("archived"), "已归档题目不可修改，请复制为新题");
            version = input.expectedVersion() + 1;
            db.update(
                    "UPDATE exam_questions SET"
                        + " category_id=?,code=?,type=?,difficulty=?,stem=?,tags_json=?,status=?,current_version=?,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=?",
                    input.categoryId(),
                    input.code(),
                    input.type(),
                    input.difficulty(),
                    input.stem(),
                    encode(input.tags()),
                    input.status(),
                    version,
                    id);
        }
        QuestionInput snapshot =
                new QuestionInput(
                        id,
                        version,
                        input.bankId(),
                        input.categoryId(),
                        input.code(),
                        input.type(),
                        input.difficulty(),
                        input.stem(),
                        input.options(),
                        input.standardAnswer(),
                        input.gradingRule(),
                        input.suggestedScore(),
                        input.analysis(),
                        input.tags(),
                        input.status());
        insert(
                "INSERT INTO exam_question_versions(question_id,version_no,content_json,created_by)"
                        + " VALUES(?,?,?,?)",
                id,
                version,
                encode(snapshot),
                BCtx.getId());
        audit("question.save", id);
        return view(snapshot, false);
    }

    @Transactional
    public void deleteDraft(long id) {
        QuestionInput q = version(id, null);
        managedBank(q.bankId(), true);
        q = version(id, null);
        require(q.status().equals("draft") && q.expectedVersion() == 1, "仅初始草稿可以删除，其他题目请归档以保留历史");
        db.update("DELETE FROM exam_question_versions WHERE question_id=?", id);
        db.update("DELETE FROM exam_questions WHERE id=?", id);
        audit("question.delete", id);
    }

    private QuestionInput version(long id, Integer requestedVersion) {
        int v =
                requestedVersion == null
                        ? ((Number)
                                        one(
                                                        "SELECT current_version FROM exam_questions"
                                                                + " WHERE id=?",
                                                        id)
                                                .get("current_version"))
                                .intValue()
                        : requestedVersion;
        return decode(
                (String)
                        one(
                                        "SELECT content_json FROM exam_question_versions WHERE"
                                                + " question_id=? AND version_no=?",
                                        id,
                                        v)
                                .get("content_json"));
    }

    private Map<String, Object> view(QuestionInput q, boolean student) {
        Map<String, Object> value =
                json.convertValue(q, new TypeReference<LinkedHashMap<String, Object>>() {});
        value.put("version", q.expectedVersion());
        value.remove("expectedVersion");
        if (student) {
            value.remove("standardAnswer");
            value.remove("analysis");
            value.remove("gradingRule");
        }
        return value;
    }

    public Map<String, Object> detail(VersionInput input) {
        QuestionInput q = version(input.id(), input.version());
        managedBank(q.bankId(), false);
        return view(q, false);
    }

    public List<Map<String, Object>> versions(long id) {
        QuestionInput q = version(id, null);
        managedBank(q.bankId(), false);
        return db.query(
                "SELECT version_no,created_by,created_at FROM exam_question_versions WHERE"
                        + " question_id=? ORDER BY version_no DESC",
                (rs, index) ->
                        Map.<String, Object>of(
                                "version",
                                rs.getInt(1),
                                "createdBy",
                                rs.getInt(2),
                                "createdAt",
                                rs.getTimestamp(3)),
                id);
    }

    @Transactional
    public Map<String, Object> changeStatus(StatusInput input) {
        QuestionInput q = version(input.id(), null);
        return saveQuestion(
                new QuestionInput(
                        q.id(),
                        input.expectedVersion(),
                        q.bankId(),
                        q.categoryId(),
                        q.code(),
                        q.type(),
                        q.difficulty(),
                        q.stem(),
                        q.options(),
                        q.standardAnswer(),
                        q.gradingRule(),
                        q.suggestedScore(),
                        q.analysis(),
                        q.tags(),
                        input.status()));
    }

    public Map<String, Object> questions(Query query, boolean student) {
        require(query.bankId() != null, "请选择题库");
        if (student) practiceBank(query.bankId(), false);
        else managedBank(query.bankId(), false);
        StringBuilder where = new StringBuilder(" WHERE q.bank_id=?");
        List<Object> args = new ArrayList<>();
        args.add(query.bankId());
        if (student) where.append(" AND q.status='enabled' AND q.type<>'short_answer'");
        else filter(where, args, "q.status", query.status());
        filter(where, args, "q.type", query.type());
        filter(where, args, "q.difficulty", query.difficulty());
        if (query.categoryId() != null) {
            where.append(" AND q.category_id=?");
            args.add(query.categoryId());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" AND (LOCATE(?,q.stem)>0 OR LOCATE(?,q.code)>0)");
            args.add(query.keyword());
            args.add(query.keyword());
        }
        if (query.tag() != null && !query.tag().isBlank()) {
            where.append(" AND LOCATE(?,q.tags_json)>0");
            args.add(encode(query.tag()));
        }
        long total =
                db.queryForObject(
                        "SELECT COUNT(*) FROM exam_questions q" + where,
                        Long.class,
                        args.toArray());
        args.add(query.size());
        args.add((query.page() - 1) * query.size());
        List<Map<String, Object>> items =
                db.query(
                        "SELECT v.content_json FROM exam_questions q JOIN exam_question_versions v"
                                + " ON v.question_id=q.id AND v.version_no=q.current_version"
                                + where
                                + " ORDER BY q.id DESC LIMIT ? OFFSET ?",
                        (rs, index) -> view(decode(rs.getString(1)), student),
                        args.toArray());
        return Map.of("items", items, "total", total, "page", query.page(), "size", query.size());
    }

    private void filter(StringBuilder sql, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append("=?");
            args.add(value);
        }
    }

    @Transactional
    public Map<String, Object> importQuestions(ImportInput input) {
        managedBank(input.bankId(), true);
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < input.questions().size(); i++) {
            QuestionInput q = input.questions().get(i);
            try {
                require(q.id() == null && q.bankId().equals(input.bankId()), "仅支持向当前题库导入新题");
                require(codes.add(q.code()), "文件内编码重复：" + q.code());
                require(
                        db.queryForObject(
                                        "SELECT COUNT(*) FROM exam_questions WHERE bank_id=? AND"
                                                + " code=?",
                                        Long.class,
                                        input.bankId(),
                                        q.code())
                                == 0,
                        "编码已存在：" + q.code());
                grader.validate(q);
                validateCategory(q);
            } catch (ServiceException e) {
                throw new ServiceException("第 " + (i + 1) + " 行：" + e.getMessage());
            }
        }
        List<Object> ids = new ArrayList<>();
        for (QuestionInput q : input.questions()) ids.add(saveQuestion(q).get("id"));
        return Map.of("count", ids.size(), "ids", ids);
    }

    @Transactional
    public List<Map<String, Object>> exportQuestions(long bankId) {
        managedBank(bankId, false);
        require(
                db.queryForObject(
                                "SELECT COUNT(*) FROM exam_questions WHERE bank_id=?",
                                Long.class,
                                bankId)
                        <= 5000,
                "单次导出最多 5000 题");
        audit("question.export", bankId);
        return db.query(
                "SELECT v.content_json FROM exam_questions q JOIN exam_question_versions v ON"
                        + " q.id=v.question_id AND q.current_version=v.version_no WHERE q.bank_id=?"
                        + " ORDER BY q.id",
                (rs, index) -> view(decode(rs.getString(1)), false),
                bankId);
    }

    private Map<String, Object> practiceBank(long bankId, boolean lock) {
        Map<String, Object> row = bank(bankId, lock);
        require(
                "enabled".equals(row.get("status"))
                        && Boolean.TRUE.equals(row.get("practice_enabled")),
                "题库未开放练习");
        return row;
    }

    public List<Map<String, Object>> practiceBanks() {
        return db.query(
                "SELECT b.id,b.name,b.description,COUNT(q.id) AS question_count FROM"
                    + " exam_question_banks b JOIN exam_questions q ON q.bank_id=b.id AND"
                    + " q.status='enabled' AND q.type<>'short_answer' WHERE b.status='enabled' AND"
                    + " b.practice_enabled=TRUE GROUP BY b.id,b.name,b.description ORDER BY b.id"
                    + " DESC",
                (rs, index) ->
                        Map.<String, Object>of(
                                "id",
                                rs.getLong(1),
                                "name",
                                rs.getString(2),
                                "description",
                                rs.getString(3),
                                "questionCount",
                                rs.getLong(4)));
    }

    public Map<String, Object> practiceDetail(long id) {
        QuestionInput q = version(id, null);
        practiceBank(q.bankId(), false);
        require(q.status().equals("enabled") && !q.type().equals("short_answer"), "题目未开放自动练习");
        return view(q, true);
    }

    @Transactional
    public Map<String, Object> practice(PracticeInput input, int userId) {
        QuestionInput current = version(input.questionId(), null);
        practiceBank(current.bankId(), true);
        // Re-read after acquiring the bank lock shared by all question updates.
        current = version(input.questionId(), null);
        require(
                current.status().equals("enabled") && !current.type().equals("short_answer"),
                "题目未开放自动练习");
        // A locking read is required under MySQL REPEATABLE READ: after waiting for the bank
        // lock it must see a concurrent retry that committed, rather than an older snapshot.
        List<Map<String, Object>> previous =
                db.queryForList(
                        "SELECT * FROM exam_practice_attempts WHERE user_id=? AND request_key=? FOR"
                                + " UPDATE",
                        userId,
                        input.requestKey());
        if (!previous.isEmpty()) {
            Map<String, Object> p = previous.get(0);
            require(
                    number(p, "question_id") == input.questionId()
                            && number(p, "version_no") == input.version()
                            && p.get("answer_json").equals(encode(input.answer())),
                    "请求标识已用于其他答案，请重新提交");
            return practiceResult(p);
        }
        require(current.expectedVersion().equals(input.version()), "题目已更新，请刷新题目后重新作答");
        Grade grade = grader.grade(current, input.answer());
        long id =
                insert(
                        "INSERT INTO"
                            + " exam_practice_attempts(user_id,question_id,version_no,answer_json,score,max_score,result,request_key)"
                            + " VALUES(?,?,?,?,?,?,?,?)",
                        userId,
                        input.questionId(),
                        input.version(),
                        encode(input.answer()),
                        grade.score(),
                        grade.maxScore(),
                        grade.result(),
                        input.requestKey());
        return practiceResult(one("SELECT * FROM exam_practice_attempts WHERE id=?", id));
    }

    private Map<String, Object> practiceResult(Map<String, Object> row) {
        QuestionInput snapshot =
                version(number(row, "question_id"), ((Number) row.get("version_no")).intValue());
        return Map.of(
                "id",
                row.get("id"),
                "score",
                row.get("score"),
                "maxScore",
                row.get("max_score"),
                "result",
                row.get("result"),
                "standardAnswer",
                snapshot.standardAnswer(),
                "analysis",
                snapshot.analysis(),
                "version",
                snapshot.expectedVersion());
    }

    public Map<String, Object> practiceHistory(int userId, Query query) {
        long total =
                db.queryForObject(
                        "SELECT COUNT(*) FROM exam_practice_attempts WHERE user_id=?",
                        Long.class,
                        userId);
        List<Map<String, Object>> items =
                db
                        .queryForList(
                                "SELECT * FROM exam_practice_attempts WHERE user_id=? ORDER BY id"
                                        + " DESC LIMIT ? OFFSET ?",
                                userId,
                                query.size(),
                                (query.page() - 1) * query.size())
                        .stream()
                        .map(
                                row -> {
                                    QuestionInput snapshot =
                                            version(
                                                    number(row, "question_id"),
                                                    ((Number) row.get("version_no")).intValue());
                                    return Map.<String, Object>of(
                                            "id",
                                            row.get("id"),
                                            "questionId",
                                            row.get("question_id"),
                                            "stem",
                                            snapshot.stem(),
                                            "score",
                                            row.get("score"),
                                            "maxScore",
                                            row.get("max_score"),
                                            "result",
                                            row.get("result"),
                                            "createdAt",
                                            row.get("created_at"));
                                })
                        .toList();
        return Map.of("items", items, "total", total);
    }
}
