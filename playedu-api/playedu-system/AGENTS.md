# playedu-system 模块协作指南

## 模块职责

本模块维护数据库迁移、系统基础数据、应用配置、后台权限初始化、升级修正，以及 `@BackendPermission`、`@Log`、`@Lock` 的切面实现。

它依赖 `playedu-common` 中的实体、Service 和注解，由可执行应用扫描并加载。

## 启动检查组成

各检查类实现 `CommandLineRunner`，使用 `@Order` 控制顺序：

| 类 | 主要职责 |
| --- | --- |
| `MigrationCheck` | 创建/升级核心数据库结构并记录迁移 |
| `SystemDataCheck` | 初始化系统必要数据 |
| `AppConfigCheck` | 补齐和更新默认应用配置 |
| `AdminPermissionCheck` | 补齐和更新后台权限定义 |
| `UpgradeCheck` | 执行版本升级后的配置与权限修正 |

`Migration`、`MigrationMapper` 和 `MigrationService` 维护公共 `migrations` 记录。核心迁移先于系统数据、配置和权限；考试模块迁移也依赖该记录表。

## AOP 横切能力

- `BackendPermissionAspect`：拦截 `@BackendPermission` 并校验后台权限。
- `AdminLogAspect`：拦截 `@Log`，在成功或异常后记录管理员操作。
- `LockAspect`：拦截 `@Lock`，围绕业务方法获取和释放公共锁。

注解定义在 `playedu-common`，业务模块只需依赖公共层即可声明横切语义，执行逻辑集中在本模块。

## 代码结构

```text
src/main/java/xyz/playedu/system/
├── aspectj/       # 权限、日志和锁切面
├── checks/        # 启动检查与升级逻辑
├── domain/        # Migration 实体
├── mapper/        # Migration Mapper
└── service/       # 迁移记录服务
```

## 启动检查规则

- 所有 `CommandLineRunner` 必须可重复执行，并明确 `@Order` 依赖。
- `MigrationCheck` 应先保证迁移表和核心表可用，再执行系统数据、配置、权限及升级检查。
- 新迁移使用唯一、单调且可识别的名称；已发布迁移不可改名或改变含义。
- 同时验证空数据库、已有数据库和重复启动。
- 当前没有跨实例迁移锁；不要假设多个实例并发启动一定安全。

## 权限与日志

- Controller 的权限字符串、权限常量和 `AdminPermissionCheck` 初始化数据必须一致。
- `BackendPermissionAspect` 的放行或拒绝逻辑属于安全边界，修改时覆盖超级管理员、普通角色、未登录和权限撤回。
- `AdminLogAspect` 不得持久化密码、Token、密钥、文件内容或其他敏感信息；异常日志也适用。

## 锁

`LockAspect` 当前调用 `MemoryDistributedLock`，只对当前 JVM 有效。需要跨实例互斥时必须更换为共享锁实现，并处理租约超时、所有者校验、异常释放和幂等。

## 验证

```powershell
.\mvnw.cmd -pl playedu-system -am test
.\mvnw.cmd -pl playedu-api -am test
```

迁移或初始化变更必须额外在 MySQL 上执行至少两次启动验证；切面变更需要通过实际受注解方法验证成功与失败路径。
