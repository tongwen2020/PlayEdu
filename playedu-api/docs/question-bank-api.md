# 试题库 API

试题库服务位于 `playedu-exam` 模块。管理接口前缀为 `/backend/v1/question-bank`，学员练习接口前缀为 `/api/v1/question-bank`。列表、详情、新增、修改、删除、导入、导出和练习接口全部只接受 `POST`；其他 HTTP 方法由过滤器统一返回 HTTP 405。

## MySQL HTTP 自动化验收

`QuestionBankHttpTest` 会启动随机端口的真实 HTTP 服务，通过项目使用的 MySQL 驱动执行数据库迁移、接口请求和断言，不使用 H2 或数据库兼容模式。

测试默认连接本机 Compose MySQL：

| 环境变量 | 默认值 |
| --- | --- |
| `QUESTION_BANK_TEST_DB_HOST` | `127.0.0.1` |
| `QUESTION_BANK_TEST_DB_PORT` | `23307` |
| `QUESTION_BANK_TEST_DB_NAME` | `playedu_question_bank_test` |
| `QUESTION_BANK_TEST_DB_USER` | `root` |
| `QUESTION_BANK_TEST_DB_PASS` | `playeduxyz` |

为防止误清理业务数据，`QUESTION_BANK_TEST_DB_NAME` 必须由字母、数字或下划线组成，并以 `_test` 结尾。测试运行时创建该临时库，全部用例结束后自动删除，不需要长期维护第二个数据库。测试账号需要具有创建和删除该测试库的权限。

在 `playedu-api` 目录执行：

```powershell
.\mvnw.cmd -B clean -pl playedu-api -am test
```

用例覆盖题库和分类管理、四种题型校验、题目不可变版本、整批导入回滚、答案隔离、客观题自动判分、幂等及并发提交、权限和数据归属，以及全部 20 个题库端点的 POST 方法限制。
