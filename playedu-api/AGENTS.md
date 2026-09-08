# PlayEdu 后端协作指南

## 适用范围

本文件适用于 `playedu-api/` 下的整个 Maven 多模块后端工程。进入子模块工作时，还必须读取并遵守该模块自己的 `AGENTS.md`；子模块规则用于补充或收紧本文件，不替代这里的全局约束。

## 项目定位

`playedu-api` 是 PlayEdu 企业内部培训系统的 Java 后端工程，向管理后台、PC 学员端和 H5 学员端提供统一 HTTP API。工程采用 Maven 多模块结构，将接口适配、公共基础能力、课程学习、资源管理、系统治理和考试能力分开维护，最终由同名的 `playedu-api` 应用模块组装为一个 Spring Boot 服务。

## 技术基线

| 项目 | 当前配置 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.3.4 |
| 构建 | Maven Wrapper、多模块聚合 |
| Web | Spring MVC、Validation、WebSocket |
| 数据访问 | MyBatis-Plus 3.5.7、MySQL、HikariCP；考试模块使用 `JdbcTemplate` |
| 鉴权 | Sa-Token 1.39.0、简单模式 JWT、Bearer Token |
| 文件存储 | AWS S3 SDK，支持兼容 S3/MinIO 的上传流程 |
| 其他 | Spring AOP、事务、异步任务、定时任务、Gson、Hutool、ip2region |

默认 API 端口为 `9898`，默认数据库名为 `playedu`。主配置在 `playedu-api/src/main/resources/application.yml`，数据库、连接池、JWT、并发登录和演示模式均支持环境变量覆盖。

## 模块与依赖

| 模块 | 主要职责 | 直接依赖 |
| --- | --- | --- |
| `playedu-api` | 启动入口、Controller、Request、拦截器、事件监听和定时任务 | 全部业务模块 |
| `playedu-common` | 用户、部门、权限、LDAP、配置、通用类型和基础设施 | 父工程公共依赖 |
| `playedu-course` | 课程、章节、课时、附件、可见范围和学习进度 | `playedu-common`、`playedu-resource` |
| `playedu-exam` | 题库、试卷、版本、练习和判分 | `playedu-common`、Spring JDBC |
| `playedu-resource` | 素材分类、资源元数据、视频扩展信息和上传 | `playedu-common` |
| `playedu-system` | 数据迁移、系统初始化、权限/日志/锁切面 | `playedu-common` |

依赖方向应保持为：公共层 → 资源/考试/系统领域层 → 课程领域层 → API 装配层。不要从底层模块反向引用 `playedu-api`。

## 运行时架构

请求先由 `WebMvcConfig` 注册的通用、管理端或学员端拦截器处理，再进入 Controller。Controller 负责校验、读取 `BCtx`/`FCtx` 和封装响应；领域 Service 承担业务逻辑；最终通过 MyBatis-Plus Mapper、XML Mapper 或 `JdbcTemplate` 访问 MySQL。

- `/backend/v1/**`：管理端接口，包含管理员、角色权限、用户部门、课程、资源、上传、LDAP、题库和试卷。
- `/api/v1/**`：学员端接口，包含登录、课程、课时播放、学习进度、个人信息和题库练习。
- 跨领域清理和进度更新使用 Spring 事件/监听器解耦。
- 应用启动时执行数据库迁移、系统数据、配置、权限和考试表结构检查。

## 核心业务域

- 组织与身份：学员、管理员、部门、角色、权限、登录记录、LDAP 映射与同步。
- 课程学习：课程分类、课程、章节、课时、附件、可见范围、学习记录与时长统计。
- 资源中心：资源分类、文件元数据、视频扩展信息、普通上传与分片上传。
- 考试中心：题库、题目版本、试卷草稿/发布版本、导入导出、练习与判分。
- 系统治理：应用配置、迁移、权限初始化、操作日志、限流、缓存和锁抽象。

## 目录地图

```text
playedu-api/
├── pom.xml                 # 聚合父 POM 与统一依赖/插件
├── mvnw / mvnw.cmd         # Maven Wrapper
├── Dockerfile              # 多阶段镜像构建
├── Dockerfile.local        # 使用本地 JAR 制作镜像
├── docs/                   # 后端文档
├── sql/                    # SQL 资料
├── playedu-api/            # 可执行应用与 HTTP 层
├── playedu-common/         # 公共领域与基础设施
├── playedu-course/         # 课程学习领域
├── playedu-exam/           # 考试领域
├── playedu-resource/       # 资源领域
└── playedu-system/         # 启动检查与切面
```

## 开始修改前

1. 从父工程目录执行搜索和 Maven 命令，先阅读目标模块的 `pom.xml`、同类实现、Mapper XML 和模块 `AGENTS.md`。
2. 使用 `rg`/`rg --files` 定位调用方和数据关联；不要仅按类名猜测职责。
3. 查看 `git status --short`，保留用户已有修改，不覆盖无关文件。
4. 涉及数据库、鉴权、学习进度、资源删除或启动任务时，先确认跨模块副作用和多实例行为。

## 通用实现规则

- 保持现有包名 `xyz.playedu.*`、分层和命名风格。
- Controller 负责协议适配、参数校验、权限声明与响应封装；可复用业务逻辑放到对应领域 Service。
- API 返回沿用 `JsonResponse`；业务错误沿用现有异常体系，不另造不兼容的返回格式。
- 数据访问优先沿用所在模块既有方式：MyBatis-Plus 模块使用 Domain/Service/Mapper/XML，考试模块使用 `JdbcTemplate`。
- 所有外部输入必须校验；SQL 值使用参数绑定，动态列名和排序字段必须白名单化。
- 管理端敏感写操作检查 `@BackendPermission` 和 `@Log`；日志不得记录密码、Token、密钥或大体积文件内容。
- 跨领域删除或进度更新沿用事件/监听器机制，并检查失败时的数据一致性。
- 不编辑 `target/`、构建日志、锁文件或生成物，除非任务明确要求。
- Java 源文件保留项目许可证头，并通过父 POM 的 AOSP Google Java Format 规则。

## 数据库与迁移

- 新表、字段和索引必须提供可重复执行或有迁移记录保护的升级路径。
- 已发布迁移的名称和语义不可复用；同时验证全新数据库与已升级数据库。
- 启动检查会修改结构、配置和权限数据。调试实例连接共享数据库前，确认当前分支不会执行超前迁移。
- 不在源码、测试或文档中写入真实数据库密码、JWT 密钥、LDAP 或对象存储凭据。

## 多实例约束

当前缓存、限流和 `MemoryDistributedLock` 都是 JVM 本地实现，LDAP 定时任务也会在每个实例执行。除非任务明确包含分布式改造，否则不要把现有实现描述成可直接水平扩展；修改学习心跳、登录限制、课程可见性缓存、锁或定时任务时必须评估跨实例一致性。

## 构建与验证

在 `playedu-api/` 目录按改动范围选择最小充分验证：

```powershell
# 全量测试
.\mvnw.cmd test

# 指定模块并自动构建依赖
.\mvnw.cmd -pl playedu-api -am test
.\mvnw.cmd -pl playedu-course -am test

# 编译/打包
.\mvnw.cmd -DskipTests package

# 格式检查；需要修复时再执行 spotless:apply
.\mvnw.cmd spotless:check
```

- 只改 Markdown：执行 `git diff --check`，并校验相对链接和命令路径。
- 改 Java 业务逻辑：至少运行受影响模块及其上层应用测试。
- 改 Mapper/SQL/迁移：使用 MySQL 验证关键查询和升级路径，不能只依赖编译。
- 改 API：验证成功、参数错误、未登录、无权限和资源不存在等分支。
- 无法运行某项验证时，在交付说明中明确未验证内容及原因。

## 运行提示

- API 默认端口 `9898`，配置文件位于 `playedu-api/src/main/resources/application.yml`。
- Compose 默认将 API 映射到宿主机 `9700`，MySQL 映射到 `23307`。
- 本地连接 Compose MySQL 时使用 `DB_HOST=127.0.0.1` 和宿主机映射端口。
- 多实例共享 JWT 时必须使用相同的 `SA_TOKEN_JWT_SECRET_KEY`。

## 推荐阅读顺序

1. 父工程 `pom.xml`，确认模块、版本和公共依赖。
2. `playedu-api/PlayeduApiApplication.java` 与 `application.yml`，确认启动和配置。
3. `WebMvcConfig`、拦截器和目标 Controller，理解 API 边界。
4. 按业务进入 common、course、resource 或 exam 模块。
5. 阅读 system 模块的启动检查和切面，理解迁移及横切逻辑。

## 交付要求

交付时说明修改了什么、关键文件、完成的验证以及剩余风险。不要声称未运行的测试已经通过，也不要顺带修改任务范围外的前端或部署文件。
