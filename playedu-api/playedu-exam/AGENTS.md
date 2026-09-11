# playedu-exam 模块协作指南

## 模块职责

本模块维护题库、题目、固定试卷草稿与发布版本、导入导出、逐题/整卷练习记录和服务端判分。核心数据访问使用 `JdbcTemplate`，不要未经整体重构就在同一流程中混用 MyBatis-Plus。

它依赖 `playedu-common` 和 Spring JDBC，Service 直接执行参数化 SQL，并主要使用 `Map<String, Object>` 组织查询结果。

## 核心组成

| 文件 | 职责 |
| --- | --- |
| `QuestionBankService` | 题库/分类/题目、版本、状态、导入导出、逐题/整卷练习及历史 |
| `ExamPaperService` | 试卷分类、草稿、校验、发布、版本、复制和导入导出 |
| `QuestionGrader` | 题目合法性校验和自动判分 |
| `QuestionBankTypes` | 题库输入、答案、评分规则和结果 record |
| `ExamPaperTypes` | 试卷、分区、题目项、版本和状态 record |
| `QuestionBankMigration` | 启动时安装题库相关表 |
| `ExamPaperMigration` | 启动时安装试卷相关表 |
| `PracticeAutoGradingMigration` | 增量安装整卷答卷表并登记迁移 |

## 数据模型

题库侧包含审计、题库、题目分类、题目、题目版本、逐题练习记录和整卷答卷表；试卷侧包含审计、试卷分类、试卷主表、草稿分区、草稿题目项和发布版本表。完整基线建表 SQL 统一维护在 `../sql/create_tables.sql`，构建时作为类路径资源供迁移类复用；整卷答卷还由增量迁移兼容已有数据库，迁移完成项写入公共 `migrations` 表。

## 业务流程与特征

- 题目采用草稿和版本机制，支持状态切换、批量导入与导出。
- 试卷由分区和题目项组成，发布前校验，发布后固化为版本 JSON。
- 判分策略包含 `exact_match` 和 `manual`，自动判分由 `QuestionGrader` 执行。
- 学员端可获取开放题库和不含答案的客观题，既保留逐题提交兼容接口，也支持整卷一次提交、自动评分和整卷历史查询。
- 后台写操作将管理员、动作和目标写入审计表。

### 整卷自动阅卷

- `submitPracticePaper` 在题库锁内读取当前所有启用的非简答题，按题目当前版本评分并写入一条不可变整卷答卷。
- 客户端只发送已作答项；服务端为未答题构造空答案并计 0 分。答案不得重复，也不得包含其他题库的题目。
- 已提交题目的版本必须仍为当前版本；选择题按选项集合精确匹配，判断题必须区分 `false` 与未作答。
- 响应和持久化快照包含总分、满分、题数、正确数及逐题得分、标准答案和解析。
- `(user_id, request_key)` 唯一约束保证幂等。相同请求键只能重放同一题库、同一答案 JSON，不能复用于另一份答卷。
- `practicePaperHistory` 返回整卷摘要；旧 `practiceHistory` 只查询逐题表，两类历史不会自动合并。

## API 边界

Controller 位于应用模块：后台路径为 `/backend/v1/question-bank/**`、`/backend/v1/exam-paper/**`，学员练习路径为 `/api/v1/question-bank/**`。对应 HTTP 测试为 `QuestionBankHttpTest` 和 `ExamPaperHttpTest`。

## 实现约束

- SQL 值必须使用占位符绑定；动态排序、列名或条件片段必须来自固定白名单。
- 输入和输出契约集中在 `QuestionBankTypes`、`ExamPaperTypes` 的 record 中，修改后同步检查 Controller、JSON 和测试。
- 题目及试卷发布版本是历史快照。已发布 JSON 要保持向后兼容，不应随草稿更新而改变。
- `QuestionGrader` 的校验与评分必须覆盖题型、选项、答案、分值和人工评分策略的边界。
- 后台修改操作保留审计记录，学员查询不得泄露标准答案或后台字段。

## 数据迁移

`QuestionBankMigration` 和 `ExamPaperMigration` 在启动阶段加载共享的基线建表 SQL，`PracticeAutoGradingMigration` 使用独立迁移标识创建整卷答卷表。新增结构时使用新的迁移标识、保证重复启动安全，并验证公共 `migrations` 表已经可用。不要修改已执行迁移的名称或历史语义。

## 验证

```powershell
.\mvnw.cmd -pl playedu-exam -am test
.\mvnw.cmd -pl playedu-api -am "-Dtest=QuestionBankHttpTest,ExamPaperHttpTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

至少覆盖草稿保存、校验、发布、版本读取、复制、状态切换、导入导出、逐题兼容提交、整卷提交、未答计零、版本冲突、幂等重放、两类历史、自动/人工评分边界以及权限隔离。涉及 SQL 时使用 MySQL，同时验证空库安装和已有库升级。
