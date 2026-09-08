# playedu-common 模块协作指南

## 模块职责

本模块提供所有后端模块共享的组织、账号、权限、配置、LDAP、响应、分页、异常、缓存和工具能力，是依赖图的基础层。

## 主要业务能力

- 用户与组织：`User`、`Department`、`UserDepartment` 及其 Service/Mapper。
- 后台账号与权限：管理员、角色、权限及关联关系和后台鉴权服务。
- 登录与上下文：前后台鉴权、`FCtx`、`BCtx`、登录记录和上传图片记录。
- LDAP：配置、连接工具、用户/部门映射、同步记录及同步明细。
- 应用配置：`AppConfig`、配置常量和 `PlayEduConfig`。
- 基础设施：限流、内存缓存、锁、S3、IP 归属地、隐私脱敏、请求和字符串工具。

## 代码结构

| 目录 | 内容 |
| --- | --- |
| `annotation` | `@BackendPermission`、`@Lock`、`@Log` |
| `bus` | 后台用户和 LDAP 的跨 Service 编排 |
| `config` | Sa-Token、MyBatis-Plus、鉴权、应用属性和 Bean 命名 |
| `constant` | 前后台、权限、配置、业务类型和系统常量 |
| `context` | 当前学员/管理员请求上下文 |
| `domain` | 公共领域实体 |
| `mapper`、`resources/mapper` | Mapper 接口与自定义 SQL |
| `service`、`service/impl` | 领域服务接口与实现 |
| `types` | 统一响应、上传、LDAP、分页和查询投影 |
| `util` | LDAP、S3、IP、缓存、锁及通用工具 |

## 数据访问与公共契约

领域 Service 多数继承 MyBatis-Plus `IService`，实现类继承 `ServiceImpl`；复杂统计和关联查询放在 XML Mapper 中。API 使用 `JsonResponse` 统一响应，分页使用 `PaginationResult` 和业务 Filter，其他模块直接复用这些契约。

## 模块协作

- `playedu-api` 使用本模块完成鉴权、上下文、响应和用户/部门管理。
- `playedu-course` 复用用户、部门、分页和通用查询投影。
- `playedu-resource` 复用配置、S3、分页和异常体系。
- `playedu-system` 实现公共注解的切面，并初始化权限和配置。
- `playedu-exam` 通过 `BackendBus` 获取后台操作者并复用异常规范。

## 依赖边界

- 不得依赖 `playedu-api`、`playedu-course`、`playedu-resource`、`playedu-exam` 或 `playedu-system`。
- 领域专属实体、查询和规则不要为了方便放入公共模块。
- `@BackendPermission`、`@Log`、`@Lock` 等注解定义在这里，其切面实现位于 `playedu-system`；修改契约时必须同步检查切面。

## 数据与服务约定

- 实体放在 `domain`，Mapper 接口与 XML 保持名称和字段映射一致。
- 复杂查询结果优先使用明确的投影类型；分页沿用 `PaginationResult` 和现有 Filter。
- 用户、部门、角色或权限模型发生变化时，检查系统初始化、API Controller 和关联清理监听器。
- 公共工具应无业务副作用、边界清晰，并为异常输入定义行为。

## 本地状态警告

`MemoryCacheUtil`、`MemoryDistributedLock` 和 `MemoryRateLimiterServiceImpl` 仅在单 JVM 内有效。涉及这些类时：

- 不得将其描述为真正的分布式实现。
- 不要依赖本地缓存作为持久数据的唯一来源。
- 缓存失效必须覆盖所有写入口；多实例需求应使用共享存储或消息通知。
- 锁保护的数据库写操作仍需依赖唯一约束、乐观锁或事务保证最终正确性。

## 安全约束

LDAP、S3、JWT 和数据库凭据只能来自外部配置。脱敏、IP 解析、鉴权上下文和统一异常的改动属于高影响改动，需要检查前后台两条调用链。

## 验证

```powershell
.\mvnw.cmd -pl playedu-common -am test
.\mvnw.cmd -pl playedu-api -am test
```

公共模块没有充分测试覆盖时，应至少编译全部依赖它的模块，并针对改变的公共契约补充测试。
