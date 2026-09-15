# eleadinedu-exam 模块协作指南

## 模块职责

本模块维护题库、题目、固定试卷草稿与发布版本、导入导出、开放题库练习、固定试卷答卷、统一考试记录和服务端判分。核心数据访问使用 `JdbcTemplate`，不要未经整体重构就在同一流程中混用 MyBatis-Plus。

它依赖 `eleadinedu-common` 和 Spring JDBC，Service 直接执行参数化 SQL，并主要使用 `Map<String, Object>` 组织查询结果。

## 核心组成

| 文件 | 职责 |
| --- | --- |
| `QuestionBankService` | 题库/分类/题目、版本、状态、导入导出、逐题/整卷练习及历史 |
| `ExamPaperService` | 试卷管理、学员固定试卷测评、考试记录列表与答题快照 |
| `QuestionGrader` | 题目合法性校验和自动判分 |
| `QuestionBankTypes` | 题库输入、答案、评分规则和结果 record |
| `ExamPaperTypes` | 试卷、分区、题目项、版本和状态 record |
| `QuestionBankMigration` | 启动时安装题库相关表 |
| `ExamPaperMigration` | 启动时安装试卷相关表 |
| `PracticeAutoGradingMigration` | 增量安装整卷答卷表并登记迁移 |
| `FixedPaperAttemptMigration` / `ExamPaperPassScoreMigration` | 安装固定试卷答卷与及格分字段 |
| `ExamRecordMigration` | 安装统一考试记录表并回填既有固定试卷答卷 |

## 数据模型

题库侧包含审计、题库、题目分类、题目、题目版本、逐题练习记录和开放题库整卷答卷；试卷侧包含审计、分类、主表、草稿分区/题目项、发布版本、固定试卷答卷和统一 `exam_records`。完整基线建表 SQL 统一维护在 `../sql/create_tables.sql`，构建时作为类路径资源供迁移类复用；各增量迁移兼容已有数据库，完成项写入公共 `migrations` 表。

## 业务流程与特征

- 题目采用草稿和版本机制，支持状态切换、批量导入与导出。
- 试卷由分区和题目项组成，发布前校验，发布后固化为版本 JSON。
- 判分策略包含 `exact_match` 和 `manual`，自动判分由 `QuestionGrader` 执行。
- 学员端可获取开放题库和不含答案的客观题，既保留逐题提交兼容接口，也支持整卷一次提交、自动评分和整卷历史查询。
- PC 学员端可列出当前已发布固定试卷、读取脱敏发布快照并幂等交卷；全客观题答卷自动判分后同时生成固定试卷答卷和统一考试记录。
- 后台写操作将管理员、动作和目标写入审计表。

### 整卷自动阅卷

- `submitPracticePaper` 在题库锁内读取当前所有启用的非简答题，按题目当前版本评分并写入一条不可变整卷答卷。
- 客户端只发送已作答项；服务端为未答题构造空答案并计 0 分。答案不得重复，也不得包含其他题库的题目。
- 已提交题目的版本必须仍为当前版本；选择题按选项集合精确匹配，判断题必须区分 `false` 与未作答。
- 响应和持久化快照包含总分、满分、题数、正确数及逐题得分、标准答案和解析。
- `(user_id, request_key)` 唯一约束保证幂等。相同请求键只能重放同一题库、同一答案 JSON，不能复用于另一份答卷。
- `practicePaperHistory` 返回整卷摘要；旧 `practiceHistory` 只查询逐题表，两类历史不会自动合并。

### 固定试卷测评与考试记录

- `studentPapers` 只列出 `published` 且 `current_version > 0` 的试卷；`studentDetail` 返回当前发布版本并移除答案、评分规则、解析和后台字段。
- 含主观题/`requires_manual_grading` 的试卷当前不允许在线详情与自动交卷；不要创建伪自动评分结果。
- `submitStudentPaper` 锁定当前发布版本，按发布 JSON 快照评分，未答计零，并校验答案只属于试卷且题目版本一致。
- `(user_id, request_key)` 唯一约束保证固定试卷交卷幂等；重放必须匹配同一试卷、版本和答案 JSON。
- 新交卷在同一事务中写 `exam_fixed_paper_attempts` 和 `exam_records`；迁移会将历史固定试卷答卷幂等回填到记录表。
- `studentRecords` 支持本人/指定学员的关键词、通过状态和分页查询；Controller 负责决定调用者身份与后台权限。
- `studentRecordDetail` 组合发布版本、提交答案和评分 JSON 还原不可变快照，并以 `recordId + userId` 校验所有权。不要用当前题目或草稿覆盖历史内容。

## API 边界

Controller 位于应用模块：后台路径为 `/backend/v1/question-bank/**`、`/backend/v1/exam-paper/**` 和 `/backend/v1/user/{userId}/exam-records/**`；学员路径为 `/api/v1/question-bank/**` 与 `/api/v1/exam-paper/**`。对应 HTTP 测试为 `QuestionBankHttpTest` 和 `ExamPaperHttpTest`。

## 实现约束

- SQL 值必须使用占位符绑定；动态排序、列名或条件片段必须来自固定白名单。
- 输入和输出契约集中在 `QuestionBankTypes`、`ExamPaperTypes` 的 record 中，修改后同步检查 Controller、JSON 和测试。
- 题目及试卷发布版本是历史快照。已发布 JSON 要保持向后兼容，不应随草稿更新而改变。
- `QuestionGrader` 的校验与评分必须覆盖题型、选项、答案、分值和人工评分策略的边界。
- 后台修改操作保留审计记录，学员查询不得泄露标准答案或后台字段。

## 数据迁移

`QuestionBankMigration` 和 `ExamPaperMigration` 在启动阶段加载共享基线 SQL；`PracticeAutoGradingMigration`、`FixedPaperAttemptMigration`、`ExamPaperPassScoreMigration`、`ExamRecordMigration` 分别兼容既有库。新增结构时使用新的迁移标识、保证重复启动安全，并验证公共 `migrations` 表已可用。`ExamRecordMigration` 含历史回填，修改时额外验证无重复记录和外键顺序。不要修改已执行迁移的名称或历史语义。

## 验证

```powershell
.\mvnw.cmd -pl eleadinedu-exam -am test
.\mvnw.cmd -pl eleadinedu-api -am "-Dtest=QuestionBankHttpTest,ExamPaperHttpTest" -Dsurefire.failIfNoSpecifiedTests=false test
```

至少覆盖草稿保存、校验、发布、版本读取、复制、状态切换、导入导出、逐题兼容提交、两类整卷提交、未答计零、发布版本冲突、幂等重放、练习历史、考试记录筛选与详情、本人/管理员隔离、历史回填、自动/人工评分边界。涉及 SQL 时使用 MySQL，同时验证空库安装、已有库升级和重复启动。
