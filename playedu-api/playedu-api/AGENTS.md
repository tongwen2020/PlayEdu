# playedu-api 模块协作指南

## 模块职责

本模块是 Spring Boot 启动与 HTTP 适配层。它组装所有领域模块，包含 Controller、Request、拦截器、事件/监听器、登录编排、缓存入口和 LDAP 调度。业务规则应尽量下沉到对应领域模块。

## 接口约定

- 管理端使用 `/backend/v1/**`，Controller 放在 `controller/backend`。
- 学员端使用 `/api/v1/**`，Controller 放在 `controller/frontend`。
- 入参模型放在对应的 `request/backend` 或 `request/frontend`，使用 Jakarta Validation。
- 返回沿用 `JsonResponse`，异常由统一异常处理转换；不要在新接口中引入另一套响应协议。
- 管理端写接口检查权限、操作日志和演示模式；学员端接口通过 `FCtx` 获取当前用户。
- 新增无需登录的路径时，必须同步审查前后台白名单，避免过度放行。

## 代码边界

- Controller 不直接承载复杂 SQL、事务编排或可复用领域规则。
- 跨模块清理优先发布既有风格的 Spring 事件，并为事件增加明确监听器。
- 事件只在当前 JVM 内传播；不要把它当成跨实例消息总线。
- `LoginLimitCache`、`LoginLockCache` 及课时心跳锁均为本地内存语义，修改时评估多实例重复执行。
- `LDAPSchedule` 会在每个 API 实例运行；调度改动必须考虑单实例执行、幂等或分布式锁。

## 配置与启动

- 启动类：`src/main/java/xyz/playedu/api/PlayeduApiApplication.java`。
- 主配置：`src/main/resources/application.yml`。
- 不提交真实密钥；新增配置优先支持环境变量覆盖并提供安全默认行为。
- 保持组件扫描覆盖 `xyz.playedu`，Mapper 扫描覆盖各领域模块。

## 验证

```powershell
.\mvnw.cmd -pl playedu-api -am test
.\mvnw.cmd -pl playedu-api -am package -DskipTests
```

修改题库或试卷 HTTP 行为时，重点运行 `QuestionBankHttpTest`、`ExamPaperHttpTest`。新增接口至少验证鉴权、参数校验、成功响应和主要异常分支。

