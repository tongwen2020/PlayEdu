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
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.playedu.common.bus.BackendBus;
import xyz.playedu.common.context.BCtx;
import xyz.playedu.common.exception.ServiceException;
import xyz.playedu.exam.ExamPaperTypes.*;

/** Fixed-paper drafts and immutable publication snapshots. */
@Service
public class ExamPaperService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final BackendBus backendBus;

    public ExamPaperService(JdbcTemplate db, ObjectMapper json, BackendBus backendBus) {
        this.db = db;
        this.json = json;
        this.backendBus = backendBus;
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new ServiceException(message);
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = db.queryForList(sql, args);
        require(!rows.isEmpty(), "记录不存在或已删除");
        return rows.get(0);
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
            throw new IllegalStateException("Cannot serialize exam paper", e);
        }
    }

    private Map<String, Object> decode(String value) {
        try {
            return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored exam paper", e);
        }
    }

    private void audit(String action, long targetId) {
        db.update(
                "INSERT INTO exam_paper_audit(admin_id,action,target_id) VALUES(?,?,?)",
                BCtx.getId(),
                action,
                targetId);
    }

    private Map<String, Object> paper(long id, boolean lock) {
        return one("SELECT * FROM exam_papers WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    }

    private Map<String, Object> managedPaper(long id, boolean lock) {
        Map<String, Object> row = paper(id, lock);
        require(
                BCtx.getId() != null
                        && (number(row, "owner_id") == BCtx.getId() || backendBus.isSuperAdmin()),
                "无权访问该试卷");
        return row;
    }

    private Map<String, Object> category(long id) {
        return one("SELECT * FROM exam_paper_categories WHERE id=?", id);
    }

    private void validateCategoryOwner(Long categoryId, long ownerId) {
        if (categoryId == null) return;
        require(number(category(categoryId), "owner_id") == ownerId, "试卷分类不属于试卷所有者");
    }

    public List<Map<String, Object>> categories() {
        List<Map<String, Object>> rows =
                backendBus.isSuperAdmin()
                        ? db.queryForList(
                                "SELECT * FROM exam_paper_categories ORDER BY owner_id,id")
                        : db.queryForList(
                                "SELECT * FROM exam_paper_categories WHERE owner_id=? ORDER BY id",
                                BCtx.getId());
        return rows.stream()
                .map(
                        row ->
                                Map.<String, Object>of(
                                        "id",
                                        row.get("id"),
                                        "ownerId",
                                        row.get("owner_id"),
                                        "parentId",
                                        row.get("parent_id"),
                                        "name",
                                        row.get("name")))
                .toList();
    }

    @Transactional
    public long createCategory(CategoryInput input) {
        long parent = input.parentId();
        int depth = 1;
        while (parent != 0) {
            Map<String, Object> row = category(parent);
            require(number(row, "owner_id") == BCtx.getId(), "上级分类不属于当前管理员");
            parent = number(row, "parent_id");
            require(++depth <= 3, "试卷分类最多三级");
        }
        long id =
                insert(
                        "INSERT INTO exam_paper_categories(owner_id,parent_id,name) VALUES(?,?,?)",
                        BCtx.getId(),
                        input.parentId(),
                        input.name().trim());
        audit("paper-category.create", id);
        return id;
    }

    @Transactional
    public void renameCategory(CategoryRename input) {
        Map<String, Object> row = category(input.id());
        require(number(row, "owner_id") == BCtx.getId() || backendBus.isSuperAdmin(), "无权修改该试卷分类");
        db.update(
                "UPDATE exam_paper_categories SET name=? WHERE id=?",
                input.name().trim(),
                input.id());
        audit("paper-category.rename", input.id());
    }

    @Transactional
    public void deleteCategory(long id) {
        Map<String, Object> row = category(id);
        require(number(row, "owner_id") == BCtx.getId() || backendBus.isSuperAdmin(), "无权删除该试卷分类");
        require(
                db.queryForObject(
                                "SELECT COUNT(*) FROM exam_paper_categories WHERE parent_id=?",
                                Long.class,
                                id)
                        == 0,
                "请先删除子分类");
        require(
                db.queryForObject(
                                "SELECT COUNT(*) FROM exam_papers WHERE category_id=?",
                                Long.class,
                                id)
                        == 0,
                "分类仍有试卷，无法删除");
        db.update("DELETE FROM exam_paper_categories WHERE id=?", id);
        audit("paper-category.delete", id);
    }

    private void validateStructure(PaperInput input) {
        require(new HashSet<>(input.tags()).size() == input.tags().size(), "试卷标签不能重复");
        Set<Integer> sectionPositions = new HashSet<>();
        Set<Long> questions = new HashSet<>();
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (PaperSectionInput section : input.sections()) {
            require(sectionPositions.add(section.position()), "大题排序值不能重复");
            Set<Integer> itemPositions = new HashSet<>();
            for (PaperItemInput item : section.items()) {
                require(itemPositions.add(item.position()), "同一大题内试题排序值不能重复");
                require(questions.add(item.questionId()), "同一道试题不能在一份试卷中重复出现");
                total = total.add(item.score());
                require(++count <= 500, "一份试卷最多包含 500 道题");
            }
        }
        require(total.compareTo(new BigDecimal("1000000")) <= 0, "试卷总分不能超过 1000000");
    }

    private Map<String, Object> question(long questionId, int version, long paperOwner) {
        Map<String, Object> row =
                one(
                        "SELECT q.bank_id,q.status,q.current_version,b.owner_id AS bank_owner_id,"
                                + "v.content_json FROM exam_questions q JOIN exam_question_banks b"
                                + " ON b.id=q.bank_id JOIN exam_question_versions v ON"
                                + " v.question_id=q.id AND v.version_no=? WHERE q.id=?",
                        version,
                        questionId);
        require(
                number(row, "bank_owner_id") == paperOwner || backendBus.isSuperAdmin(),
                "试题不属于试卷所有者的题库");
        return row;
    }

    private Map<String, Object> questionContent(Map<String, Object> row, int version) {
        Map<String, Object> result = decode((String) row.get("content_json"));
        result.remove("expectedVersion");
        result.put("version", version);
        return result;
    }

    @Transactional
    public Map<String, Object> save(PaperInput input) {
        validateStructure(input);
        long id;
        long ownerId;
        if (input.id() == null) {
            ownerId = Objects.requireNonNull(BCtx.getId()).longValue();
            validateCategoryOwner(input.categoryId(), ownerId);
            id =
                    insert(
                            "INSERT INTO exam_papers(owner_id,category_id,code,name,description,"
                                    + "tags_json) VALUES(?,?,?,?,?,?)",
                            ownerId,
                            input.categoryId(),
                            input.code(),
                            input.name().trim(),
                            input.description(),
                            encode(input.tags()));
        } else {
            Map<String, Object> old = managedPaper(input.id(), true);
            require(!"archived".equals(old.get("status")), "已归档试卷不可修改，请复制为新试卷");
            require(
                    input.revision() != null && number(old, "revision") == input.revision(),
                    "试卷已更新，请刷新后重试");
            id = input.id();
            ownerId = number(old, "owner_id");
            validateCategoryOwner(input.categoryId(), ownerId);
            db.update(
                    "UPDATE exam_papers SET"
                        + " category_id=?,code=?,name=?,description=?,tags_json=?,status='draft',revision=revision+1,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=?",
                    input.categoryId(),
                    input.code(),
                    input.name().trim(),
                    input.description(),
                    encode(input.tags()),
                    id);
            db.update("DELETE FROM exam_paper_draft_items WHERE paper_id=?", id);
            db.update("DELETE FROM exam_paper_draft_sections WHERE paper_id=?", id);
        }
        for (PaperSectionInput section : input.sections()) {
            long sectionId =
                    insert(
                            "INSERT INTO exam_paper_draft_sections"
                                    + "(paper_id,title,description,position,shuffle_questions)"
                                    + " VALUES(?,?,?,?,?)",
                            id,
                            section.title().trim(),
                            section.description(),
                            section.position(),
                            section.shuffleQuestions());
            for (PaperItemInput item : section.items()) {
                question(item.questionId(), item.questionVersion(), ownerId);
                insert(
                        "INSERT INTO exam_paper_draft_items"
                            + "(paper_id,section_id,question_id,question_version,score,position)"
                            + " VALUES(?,?,?,?,?,?)",
                        id,
                        sectionId,
                        item.questionId(),
                        item.questionVersion(),
                        item.score(),
                        item.position());
            }
        }
        audit("paper.save", id);
        return detail(new VersionInput(id, null));
    }

    private Map<String, Object> draft(long id, Map<String, Object> p) {
        long ownerId = number(p, "owner_id");
        Map<String, Object> result = paperMetadata(p);
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Map<String, Object> section :
                db.queryForList(
                        "SELECT * FROM exam_paper_draft_sections WHERE paper_id=? ORDER BY"
                                + " position,id",
                        id)) {
            Map<String, Object> sectionView = new LinkedHashMap<>();
            sectionView.put("title", section.get("title"));
            sectionView.put("description", section.get("description"));
            sectionView.put("position", section.get("position"));
            sectionView.put("shuffleQuestions", section.get("shuffle_questions"));
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> item :
                    db.queryForList(
                            "SELECT * FROM exam_paper_draft_items WHERE section_id=? ORDER BY"
                                    + " position,id",
                            section.get("id"))) {
                int version = ((Number) item.get("question_version")).intValue();
                Map<String, Object> q = question(number(item, "question_id"), version, ownerId);
                Map<String, Object> itemView = new LinkedHashMap<>();
                itemView.put("questionId", item.get("question_id"));
                itemView.put("questionVersion", version);
                itemView.put("score", item.get("score"));
                itemView.put("position", item.get("position"));
                itemView.put("question", questionContent(q, version));
                items.add(itemView);
            }
            sectionView.put("items", items);
            sections.add(sectionView);
        }
        result.put("sections", sections);
        result.putAll(summary(sections));
        return result;
    }

    private Map<String, Object> paperMetadata(Map<String, Object> p) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", p.get("id"));
        result.put("ownerId", p.get("owner_id"));
        result.put("categoryId", p.get("category_id"));
        result.put("code", p.get("code"));
        result.put("name", p.get("name"));
        result.put("description", p.get("description"));
        try {
            result.put("tags", json.readValue((String) p.get("tags_json"), List.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid paper tags", e);
        }
        result.put("mode", p.get("mode"));
        result.put("status", p.get("status"));
        result.put("revision", p.get("revision"));
        result.put("currentVersion", p.get("current_version"));
        result.put("createdAt", p.get("created_at"));
        result.put("updatedAt", p.get("updated_at"));
        return result;
    }

    private Map<String, Object> summary(List<Map<String, Object>> sections) {
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal objective = BigDecimal.ZERO;
        BigDecimal subjective = BigDecimal.ZERO;
        for (Map<String, Object> section : sections) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) section.get("items");
            for (Map<String, Object> item : items) {
                count++;
                BigDecimal score = decimal(item.get("score"));
                total = total.add(score);
                @SuppressWarnings("unchecked")
                Map<String, Object> q = (Map<String, Object>) item.get("question");
                if ("short_answer".equals(q.get("type"))) subjective = subjective.add(score);
                else objective = objective.add(score);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionCount", count);
        result.put("totalScore", total);
        result.put("objectiveScore", objective);
        result.put("subjectiveScore", subjective);
        result.put("requiresManualGrading", subjective.signum() > 0);
        return result;
    }

    public Map<String, Object> detail(VersionInput input) {
        Map<String, Object> p = managedPaper(input.id(), false);
        if (input.version() == null) return draft(input.id(), p);
        Map<String, Object> version =
                one(
                        "SELECT content_json FROM exam_paper_versions WHERE paper_id=? AND"
                                + " version_no=?",
                        input.id(),
                        input.version());
        return decode((String) version.get("content_json"));
    }

    public Map<String, Object> validate(long id) {
        Map<String, Object> p = managedPaper(id, false);
        Map<String, Object> content = draft(id, p);
        List<String> errors = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) content.get("sections");
        if (sections.isEmpty()) errors.add("试卷至少需要一个大题");
        for (Map<String, Object> section : sections) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) section.get("items");
            if (items.isEmpty()) errors.add("大题“" + section.get("title") + "”没有试题");
            for (Map<String, Object> item : items) {
                @SuppressWarnings("unchecked")
                Map<String, Object> q = (Map<String, Object>) item.get("question");
                Map<String, Object> current =
                        one(
                                "SELECT status,current_version FROM exam_questions WHERE id=?",
                                item.get("questionId"));
                if (!"enabled".equals(current.get("status")))
                    errors.add("试题“" + q.get("code") + "”未启用");
                else if (number(current, "current_version")
                        != ((Number) item.get("questionVersion")).longValue())
                    errors.add("试题“" + q.get("code") + "”已有新版本，请重新选择");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", List.of());
        result.put("summary", summary(sections));
        return result;
    }

    @Transactional
    public Map<String, Object> publish(RevisionInput input) {
        Map<String, Object> p = managedPaper(input.id(), true);
        require(!"archived".equals(p.get("status")), "已归档试卷不可发布");
        require(number(p, "revision") == input.expectedRevision(), "试卷已更新，请刷新后重试");
        require("draft".equals(p.get("status")), "只有草稿可以发布");
        Map<String, Object> validation = validate(input.id());
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) validation.get("errors");
        require(errors.isEmpty(), String.join("；", errors));
        int version = ((Number) p.get("current_version")).intValue() + 1;
        Map<String, Object> snapshot = draft(input.id(), p);
        snapshot.put("status", "published");
        snapshot.put("version", version);
        snapshot.put("currentVersion", version);
        snapshot.put("revision", input.expectedRevision() + 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> sums = (Map<String, Object>) validation.get("summary");
        insert(
                "INSERT INTO exam_paper_versions(paper_id,version_no,content_json,question_count,"
                    + "total_score,objective_score,subjective_score,requires_manual_grading,created_by)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                input.id(),
                version,
                encode(snapshot),
                sums.get("questionCount"),
                sums.get("totalScore"),
                sums.get("objectiveScore"),
                sums.get("subjectiveScore"),
                sums.get("requiresManualGrading"),
                BCtx.getId());
        db.update(
                "UPDATE exam_papers SET status='published',current_version=?,revision=revision+1,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE id=?",
                version,
                input.id());
        audit("paper.publish", input.id());
        return detail(new VersionInput(input.id(), version));
    }

    @Transactional
    public Map<String, Object> changeStatus(StatusInput input) {
        Map<String, Object> p = managedPaper(input.id(), true);
        require(number(p, "revision") == input.expectedRevision(), "试卷已更新，请刷新后重试");
        String from = Objects.toString(p.get("status"));
        String to = input.status();
        boolean allowed =
                (from.equals("published") && Set.of("disabled", "archived").contains(to))
                        || (from.equals("disabled")
                                && Set.of("published", "archived").contains(to));
        require(allowed, "不允许从“" + from + "”变更为“" + to + "”");
        db.update(
                "UPDATE exam_papers SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=?",
                to,
                input.id());
        audit("paper.status." + to, input.id());
        return detail(new VersionInput(input.id(), null));
    }

    public List<Map<String, Object>> versions(long id) {
        managedPaper(id, false);
        return db.query(
                "SELECT version_no,question_count,total_score,objective_score,subjective_score,"
                        + "requires_manual_grading,created_by,created_at FROM exam_paper_versions"
                        + " WHERE paper_id=? ORDER BY version_no DESC",
                (rs, index) -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("version", rs.getInt("version_no"));
                    value.put("questionCount", rs.getInt("question_count"));
                    value.put("totalScore", rs.getBigDecimal("total_score"));
                    value.put("objectiveScore", rs.getBigDecimal("objective_score"));
                    value.put("subjectiveScore", rs.getBigDecimal("subjective_score"));
                    value.put("requiresManualGrading", rs.getBoolean("requires_manual_grading"));
                    value.put("createdBy", rs.getInt("created_by"));
                    value.put("createdAt", rs.getTimestamp("created_at"));
                    return value;
                },
                id);
    }

    @Transactional
    public Map<String, Object> copy(CopyInput input) {
        Map<String, Object> source = managedPaper(input.sourceId(), false);
        Map<String, Object> content;
        if (input.version() == null) content = draft(input.sourceId(), source);
        else content = detail(new VersionInput(input.sourceId(), input.version()));
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) content.get("tags");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sectionMaps = (List<Map<String, Object>>) content.get("sections");
        List<PaperSectionInput> sections = new ArrayList<>();
        for (Map<String, Object> section : sectionMaps) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemMaps = (List<Map<String, Object>>) section.get("items");
            List<PaperItemInput> items =
                    itemMaps.stream()
                            .map(
                                    item ->
                                            new PaperItemInput(
                                                    ((Number) item.get("questionId")).longValue(),
                                                    ((Number) item.get("questionVersion"))
                                                            .intValue(),
                                                    decimal(item.get("score")),
                                                    ((Number) item.get("position")).intValue()))
                            .toList();
            sections.add(
                    new PaperSectionInput(
                            Objects.toString(section.get("title")),
                            Objects.toString(section.get("description"), ""),
                            ((Number) section.get("position")).intValue(),
                            Boolean.TRUE.equals(section.get("shuffleQuestions")),
                            items));
        }
        PaperInput copy =
                new PaperInput(
                        null,
                        null,
                        input.code(),
                        input.name(),
                        Objects.toString(content.get("description"), ""),
                        number(source, "owner_id") == BCtx.getId()
                                ? (Long) source.get("category_id")
                                : null,
                        tags,
                        sections);
        Map<String, Object> result = save(copy);
        audit("paper.copy", ((Number) result.get("id")).longValue());
        return result;
    }

    @Transactional
    public void deleteDraft(long id) {
        Map<String, Object> p = managedPaper(id, true);
        require(
                "draft".equals(p.get("status")) && number(p, "current_version") == 0,
                "仅从未发布的草稿试卷可以删除");
        db.update("DELETE FROM exam_paper_draft_items WHERE paper_id=?", id);
        db.update("DELETE FROM exam_paper_draft_sections WHERE paper_id=?", id);
        db.update("DELETE FROM exam_papers WHERE id=?", id);
        audit("paper.delete", id);
    }

    public Map<String, Object> export(VersionInput input) {
        Map<String, Object> p = managedPaper(input.id(), false);
        int version =
                input.version() == null
                        ? ((Number) p.get("current_version")).intValue()
                        : input.version();
        require(version > 0, "草稿尚未发布，无法导出正式试卷");
        audit("paper.export", input.id());
        return detail(new VersionInput(input.id(), version));
    }

    @Transactional
    public Map<String, Object> importPapers(ImportInput input) {
        Set<String> codes = new HashSet<>();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < input.papers().size(); i++) {
            PaperInput paper = input.papers().get(i);
            require(paper.id() == null && paper.revision() == null, "第 " + (i + 1) + " 份：仅支持导入新试卷");
            require(codes.add(paper.code()), "第 " + (i + 1) + " 份：文件内试卷编码重复");
            try {
                Map<String, Object> created = save(paper);
                ids.add(((Number) created.get("id")).longValue());
            } catch (ServiceException e) {
                throw new ServiceException("第 " + (i + 1) + " 份：" + e.getMessage());
            }
        }
        return Map.of("count", ids.size(), "ids", ids);
    }

    public Map<String, Object> list(PaperQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!backendBus.isSuperAdmin()) {
            where.append(" AND p.owner_id=?");
            args.add(BCtx.getId());
        }
        if (query.categoryId() != null) {
            where.append(" AND p.category_id=?");
            args.add(query.categoryId());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            where.append(" AND (LOCATE(?,p.name)>0 OR LOCATE(?,p.code)>0)");
            args.add(query.keyword());
            args.add(query.keyword());
        }
        if (query.status() != null && !query.status().isBlank()) {
            require(
                    Set.of("draft", "published", "disabled", "archived").contains(query.status()),
                    "试卷状态不正确");
            where.append(" AND p.status=?");
            args.add(query.status());
        }
        if (query.tag() != null && !query.tag().isBlank()) {
            where.append(" AND LOCATE(?,p.tags_json)>0");
            args.add(encode(query.tag()));
        }
        long total =
                db.queryForObject(
                        "SELECT COUNT(*) FROM exam_papers p" + where, Long.class, args.toArray());
        args.add(query.size());
        args.add((query.page() - 1) * query.size());
        List<Map<String, Object>> items =
                db
                        .queryForList(
                                "SELECT p.* FROM exam_papers p"
                                        + where
                                        + " ORDER BY p.id DESC LIMIT ? OFFSET ?",
                                args.toArray())
                        .stream()
                        .map(
                                p -> {
                                    Map<String, Object> view = paperMetadata(p);
                                    int version = ((Number) p.get("current_version")).intValue();
                                    Map<String, Object> sums;
                                    if (!"draft".equals(p.get("status")) && version > 0) {
                                        Map<String, Object> v =
                                                one(
                                                        "SELECT * FROM exam_paper_versions WHERE"
                                                                + " paper_id=? AND version_no=?",
                                                        p.get("id"),
                                                        version);
                                        sums = new LinkedHashMap<>();
                                        sums.put("questionCount", v.get("question_count"));
                                        sums.put("totalScore", v.get("total_score"));
                                        sums.put("objectiveScore", v.get("objective_score"));
                                        sums.put("subjectiveScore", v.get("subjective_score"));
                                        sums.put(
                                                "requiresManualGrading",
                                                v.get("requires_manual_grading"));
                                    } else {
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> sections =
                                                (List<Map<String, Object>>)
                                                        draft(number(p, "id"), p).get("sections");
                                        sums = summary(sections);
                                    }
                                    view.putAll(sums);
                                    return view;
                                })
                        .toList();
        return Map.of("items", items, "total", total, "page", query.page(), "size", query.size());
    }
}
