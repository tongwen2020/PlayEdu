# playedu-exam 模块协作指南

## 模块职责

本模块维护题库、题目、试卷草稿、发布版本、导入导出、练习记录和判分。核心数据访问使用 `JdbcTemplate`，不要未经整体重构就在同一流程中混用 MyBatis-Plus。

它依赖 `playedu-common` 和 Spring JDBC，Service 直接执行参数化 SQL，并主要使用 `Map<String, Object>` 组织查询结果。

## 核心组成

| 文件 | 职责 |
| --- | --- |
| `QuestionBankService` | 题库/分类/题目、版本、状态、导入导出、练习及历史 |
| `ExamPaperService` | 试卷分类、草稿、校验、发布、版本、复制和导入导出 |
| `QuestionGrader` | 题目合法性校验和自动判分 |
| `QuestionBankTypes` | 题库输入、答案、评分规则和结果 record |
| `ExamPaperTypes` | 试卷、分区、题目项、版本和状态 record |
| `QuestionBankMigration` | 启动时安装题库相关表 |
| `ExamPaperMigration` | 启动时安装试卷相关表 |

## 数据模型

题库侧包含审计、题库、题目分类、题目、题目版本和练习记录表；试卷侧包含审计、试卷分类、试卷主表、草稿分区、草稿题目项和发布版本表。建表 SQL 位于 `src/main/resources/db/`，迁移完成项写入公共 `migrations` 表。

## 业务流程与特征

- 题目采用草稿和版本机制，支持状态切换、批量导入与导出。
- 试卷由分区和题目项组成，发布前校验，发布后固化为版本 JSON。
- 判分策略包含 `exact_match` 和 `manual`，自动判分由 `QuestionGrader` 执行。
- 学员端可获取开放题库、查看题目、提交练习和查询历史。
- 后台写操作将管理员、动作和目标写入审计表。

## API 边界

Controller 位于应用模块：后台路径为 `/backend/v1/question-bank/**`、`/backend/v1/exam-paper/**`，学员练习路径为 `/api/v1/question-bank/**`。对应 HTTP 测试为 `QuestionBankHttpTest` 和 `ExamPaperHttpTest`。

## 实现约束

- SQL 值必须使用占位符绑定；动态排序、列名或条件片段必须来自固定白名单。
- 输入和输出契约集中在 `QuestionBankTypes`、`ExamPaperTypes` 的 record 中，修改后同步检查 Controller、JSON 和测试。
- 题目及试卷发布版本是历史快照。已发布 JSON 要保持向后兼容，不应随草稿更新而改变。
- `QuestionGrader` 的校验与评分必须覆盖题型、选项、答案、分值和人工评分策略的边界。
- 后台修改操作保留审计记录，学员查询不得泄露标准答案或后台字段。

## 数据迁移

`QuestionBankMigration` 和 `ExamPaperMigration` 在启动阶段加载 `src/main/resources/db` 中的 SQL。新增结构时使用新的迁移标识、保证重复启动安全，并验证公共 `migrations` 表已经可用。不要修改已执行迁移的历史语义。

## 验证

```powershell
.\mvnw.cmd -pl playedu-exam -am test
.\mvnw.cmd -pl playedu-api -am "-Dtest=QuestionBankHttpTest,ExamPaperHttpTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

至少覆盖草稿保存、校验、发布、版本读取、复制、状态切换、导入导出、练习提交、自动/人工判分以及权限隔离。涉及 SQL 时使用 MySQL，同时验证空库安装和已有库升级。
