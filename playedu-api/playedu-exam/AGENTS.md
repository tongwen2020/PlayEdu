# playedu-exam 模块协作指南

## 模块职责

本模块维护题库、题目、试卷草稿、发布版本、导入导出、练习记录和判分。核心数据访问使用 `JdbcTemplate`，不要未经整体重构就在同一流程中混用 MyBatis-Plus。

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
.\mvnw.cmd -pl playedu-api -am -Dtest=QuestionBankHttpTest,ExamPaperHttpTest test
```

至少覆盖草稿保存、校验、发布、版本读取、复制、状态切换、导入导出、练习提交、自动/人工判分以及权限隔离。涉及 SQL 时使用 MySQL，同时验证空库安装和已有库升级。

