# 试卷库 API

试卷库位于 `playedu-exam` 模块，管理接口前缀为 `/backend/v1/exam-paper`，全部接口仅接受 `POST`。第一阶段实现固定组卷，随机和混合组卷留待后续版本。

## 核心约束

- 试卷分类独立于课程分类和试题分类，最多三级，并按管理员所有者隔离。
- 试卷可以引用同一所有者名下多个题库的试题；超级管理员可在其管理范围内跨所有者操作。
- 草稿项固定保存 `questionId + questionVersion`，每道题的试卷分值可以覆盖试题建议分值。
- 发布前要求试题处于启用状态，而且引用的是试题当前版本。
- 发布生成不可变试卷版本，并保存包含题干、答案、解析和评分规则的完整快照。
- 编辑已发布或已停用试卷会产生新草稿；再次发布生成下一版本，不修改历史版本。
- 只有从未发布的草稿能够物理删除，归档是终态。
- 单份试卷最多 50 个大题、500 道题、总分不超过 1000000；单题分值为 0.01–10000，最多两位小数。

## 权限

| 权限 | 接口范围 |
| --- | --- |
| `exam-paper-view` | 分类列表、试卷列表、详情和版本 |
| `exam-paper-edit` | 分类维护、草稿保存、复制、导入、校验、选题和草稿删除 |
| `exam-paper-publish` | 发布、停用、重新启用和归档 |
| `exam-paper-export` | 导出包含标准答案和解析的正式试卷版本 |

除权限点外，服务层还会校验数据所有权。普通管理员只能查看和维护自己的试卷；超级管理员可查看和管理全部试卷。

## 接口清单

### 试卷分类

- `/categories/list`
- `/categories/create`：`{ "parentId": 0, "name": "入职考试" }`
- `/categories/rename`：`{ "id": 1, "name": "季度考试" }`
- `/categories/delete`：`{ "id": 1 }`

### 试卷

- `/papers/list`：支持 `categoryId`、`keyword`、`status`、`tag`、`page`、`size`
- `/papers/detail`：`{ "id": 1 }` 返回当前草稿；传 `version` 返回不可变版本
- `/papers/save`：整张保存基本信息、大题和试题项
- `/papers/validate`：返回 `valid`、`errors`、`warnings` 和分值汇总
- `/papers/publish`：`{ "id": 1, "expectedRevision": 2 }`
- `/papers/status`：目标状态支持 `published`、`disabled`、`archived`
- `/papers/copy`：从草稿或指定正式版本复制为新草稿
- `/papers/versions`
- `/papers/delete-draft`
- `/papers/import`：每批最多导入 100 份新草稿，整批事务提交
- `/papers/export`：不传版本时导出最新正式版本
- `/questions/search`：请求结构复用试题库查询参数，用于组卷选题

`/papers/save` 请求示例：

```json
{
  "code": "JAVA_ENTRY_001",
  "name": "Java 入职考试",
  "description": "固定组卷",
  "categoryId": null,
  "tags": ["Java", "入职"],
  "sections": [
    {
      "title": "一、单选题",
      "description": "请选择唯一正确答案",
      "position": 0,
      "shuffleQuestions": true,
      "items": [
        {
          "questionId": 10,
          "questionVersion": 2,
          "score": 5.00,
          "position": 0
        }
      ]
    }
  ]
}
```

更新草稿时还必须传入 `id` 和当前 `revision`，用于乐观锁校验。

## MySQL 迁移与测试

服务启动时，`ExamPaperMigration` 会在当前配置的 MySQL 数据库中执行可重复启动的增量迁移 `20260907_exam_paper_v1`。新增表均使用现有数据源，不引入第二个数据库服务：

- `exam_paper_audit`
- `exam_paper_categories`
- `exam_papers`
- `exam_paper_draft_sections`
- `exam_paper_draft_items`
- `exam_paper_versions`

验收测试默认连接 `127.0.0.1:23307` 的 PlayEdu MySQL 实例，在同一实例中临时创建 `playedu_exam_paper_test`，测试完成后自动删除，避免清理或污染 `playedu` 业务库。

```powershell
.\mvnw.cmd -B -pl playedu-api -am "-Dtest=ExamPaperHttpTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

可以通过 `EXAM_PAPER_TEST_DB_HOST`、`EXAM_PAPER_TEST_DB_PORT`、`EXAM_PAPER_TEST_DB_NAME`、`EXAM_PAPER_TEST_DB_USER` 和 `EXAM_PAPER_TEST_DB_PASS` 覆盖连接参数。测试数据库名称必须以 `_test` 结尾。
