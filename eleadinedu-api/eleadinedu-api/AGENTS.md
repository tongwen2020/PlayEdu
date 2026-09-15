# eleadinedu-api 模块协作指南

## 模块职责

本模块是 Spring Boot 启动与 HTTP 适配层。它组装所有领域模块，包含 Controller、Request、拦截器、事件/监听器、登录编排、缓存入口和 LDAP 调度。业务规则应尽量下沉到对应领域模块。

它是后端唯一的可执行模块，直接依赖 `eleadinedu-common`、`eleadinedu-system`、`eleadinedu-course`、`eleadinedu-resource` 和 `eleadinedu-exam`，构建产物为 `target/eleadinedu-api.jar`。

## 启动与运行配置

- 启动类：`src/main/java/xyz/eleadinedu/api/EleadineduApiApplication.java`。
- 扫描范围：`xyz.eleadinedu` 下的组件，以及 `xyz.eleadinedu.**.mapper`。
- 启用事务、异步执行、定时任务和 Spring Boot 自动配置。
- 主配置：`src/main/resources/application.yml`；默认端口 `9898`。
- 配置覆盖范围包括 MySQL/HikariCP、Mapper 路径、Sa-Token/JWT、上传大小、异步线程池、限流和演示模式。

## 代码结构

| 目录 | 职责 |
| --- | --- |
| `controller/backend` | `/backend/v1/**` 管理端接口 |
| `controller/frontend` | `/api/v1/**` 学员端接口 |
| `request/backend`、`request/frontend` | 入参、校验规则和批量操作模型 |
| `interceptor` | 通用限流、前后台鉴权、题库方法过滤和 MVC 注册 |
| `event`、`listener` | 课程、课时、用户、部门和登录事件及副作用 |
| `bus` | 登录流程编排 |
| `cache` | 登录频率限制和登录锁 |
| `schedule` | LDAP 定时同步 |
| `controller/ExceptionController.java` | 统一异常响应转换 |

## API 能力范围

管理端覆盖管理员/角色/日志、应用配置、用户/部门、课程/章节/课时/附件、资源分类/资源/上传、LDAP、仪表盘、题库、固定试卷，以及指定学员的考试记录/答题快照。学员端覆盖密码或 LDAP 登录、课程与分类、课时播放/进度/心跳、学习记录、个人资料、开放题库练习，以及已发布固定试卷的列表、脱敏详情、幂等交卷、考试记录和本人答题快照。

统一返回模型为 `JsonResponse`。后台权限由 `AdminInterceptor` 与 `@BackendPermission` 切面共同控制，后台上下文使用 `BCtx`；学员身份由 `FrontInterceptor` 解析，学员上下文使用 `FCtx`。

## 请求处理流程

1. 请求经过 `ApiInterceptor` 及对应的前台或后台拦截器。
2. Controller 使用 Request 模型完成参数校验并调用领域 Service。
3. Service 经 Mapper/XML 或 JDBC 访问数据库。
4. Controller 使用 `JsonResponse` 输出结果。
5. 跨模块副作用通过事件交给 Listener 清理关联数据、失效缓存或更新进度。

## 接口约定

- 管理端使用 `/backend/v1/**`，Controller 放在 `controller/backend`。
- 学员端使用 `/api/v1/**`，Controller 放在 `controller/frontend`。
- 入参模型放在对应的 `request/backend` 或 `request/frontend`，使用 Jakarta Validation。
- 返回沿用 `JsonResponse`，异常由统一异常处理转换；不要在新接口中引入另一套响应协议。
- 管理端写接口检查权限、操作日志和演示模式；学员端接口通过 `FCtx` 获取当前用户。
- 新增无需登录的路径时，必须同步审查前后台白名单，避免过度放行。

## 考试 HTTP 边界

- 后台题库和试卷接口分别位于 `/backend/v1/question-bank/**`、`/backend/v1/exam-paper/**`，全部使用 POST；权限分为题库查看/编辑/导出和试卷查看/编辑/发布/导出。
- 学员练习位于 `/api/v1/question-bank/**`。题目列表隐藏标准答案和解析，只有判分结果可以返回这些字段。
- 固定试卷学员接口位于 `/api/v1/exam-paper/**`：`papers/list`、`papers/detail`、`papers/submit`、`records`、`records/detail`。当前仅 PC 接入；H5 尚未接入。
- `papers/detail` 只允许当前已发布版本且拒绝需要人工评分的试卷；交卷在版本行锁内按发布快照评分，并以 `(user_id, request_key)` 保证幂等。
- `records/detail` 必须按当前学员 ID 限定记录；管理端 `/backend/v1/user/{userId}/exam-records[/detail]` 还需 `user-learn` 权限并先校验学员存在。
- `practice/paper/history` 是开放题库整卷历史，`exam_records` 是固定试卷考试记录，二者不合并。旧 `practice/submit` 与 `practice/history` 仍是逐题兼容契约。
- 正式考试活动、考生分配、时间/次数控制、断点暂存、简答题人工阅卷和成绩发布尚未形成 HTTP 闭环。

## 代码边界

- Controller 不直接承载复杂 SQL、事务编排或可复用领域规则。
- 跨模块清理优先发布既有风格的 Spring 事件，并为事件增加明确监听器。
- 事件只在当前 JVM 内传播；不要把它当成跨实例消息总线。
- `LoginLimitCache`、`LoginLockCache` 及课时心跳锁均为本地内存语义，修改时评估多实例重复执行。
- `LDAPSchedule` 会在每个 API 实例运行；调度改动必须考虑单实例执行、幂等或分布式锁。

## 配置约束

- 不提交真实密钥；新增配置优先支持环境变量覆盖并提供安全默认行为。
- 保持组件扫描覆盖 `xyz.eleadinedu`，Mapper 扫描覆盖各领域模块。
- 生产环境必须覆盖默认数据库密码和 JWT 签名密钥。

## 验证

```powershell
.\mvnw.cmd -pl eleadinedu-api -am test
.\mvnw.cmd -pl eleadinedu-api -am package -DskipTests
```

修改题库、试卷或考试记录 HTTP 行为时，重点运行 `QuestionBankHttpTest`、`ExamPaperHttpTest`。至少验证鉴权、参数校验、发布/版本状态、主观题拒绝、幂等、本人记录隔离、管理员权限和不存在资源。
