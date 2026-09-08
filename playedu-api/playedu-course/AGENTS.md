# playedu-course 模块协作指南

## 模块职责

本模块维护课程、分类、章节、课时、附件、课程可见范围、学习记录和学习时长统计。它依赖 `playedu-common` 与 `playedu-resource`，不直接暴露 HTTP 接口。

## 核心模型

| 模型 | 用途 |
| --- | --- |
| `Course`、`CourseCategory` | 课程主数据与分类关系 |
| `CourseChapter`、`CourseHour` | 章节和课时内容编排 |
| `CourseDepartmentUser` | 课程对部门/用户的可见范围 |
| `CourseAttachment`、`CourseAttachmentDownloadLog` | 课程附件和下载审计 |
| `UserCourseRecord` | 用户的课程级进度 |
| `UserCourseHourRecord` | 用户的课时级进度与完成状态 |
| `UserLearnDurationRecord`、`UserLearnDurationStats` | 学习时长明细与统计 |
| `UserLatestLearn` | 最近学习信息投影 |

## 代码结构

- `domain`：数据库实体和查询投影。
- `mapper`、`resources/mapper`：CRUD、可见性、学习统计和分页 SQL。
- `service`、`service/impl`：课程、章节、课时、附件、记录及统计逻辑。
- `bus/UserBus`：集中判断用户是否可以查看课程。
- `caches`：用户课程可见性与最近学习时间的内存缓存。

## 主要业务流程

### 课程维护

管理端创建课程后配置分类和可见部门/用户，再按“课程—章节—课时”编排内容；课时通过资源 ID 关联资源模块。附件独立排序，并记录学员下载行为。

### 学习进度

学员请求播放信息后持续提交进度和心跳。`UserCourseHourRecordService` 更新课时记录，完成事件推动 `UserCourseRecordService` 汇总课程进度；学习时长同时写入明细并聚合到统计表。

### 课程可见性

`UserBus.canSeeCourse` 综合课程状态、公开范围、部门和指定用户判断访问权，`UserCanSeeCourseCache` 缓存结果。课程分配或组织关系改变时必须同步失效缓存。

## 模块边界

相关 HTTP 接口位于应用模块的课程、章节、课时、附件和用户学习 Controller。资源上传、元数据和播放地址由 `playedu-resource` 负责，本模块只保存资源关联；跨领域删除由应用模块事件监听器协调。

## 领域规则

- 保持“课程 → 章节 → 课时”的归属校验，不能只按子对象 ID 操作。
- 课程可见性同时涉及公开状态、部门和指定用户；修改时覆盖授权与撤权场景。
- 课时完成状态、课程总体进度、最近学习和学习时长是相互关联的数据，规则调整必须成组检查。
- 删除课程、章节、课时、用户或部门时，检查学习记录、附件、关联表、统计和缓存清理。
- 资源文件生命周期属于 `playedu-resource`；本模块保存关联，不应绕过资源服务直接操作对象存储。

## 缓存与并发

`UserCanSeeCourseCache` 和 `UserLastLearnTimeCache` 是进程内缓存，课时 `record`/`ping` 使用的锁也是进程内锁。多实例可能导致权限缓存不一致、重复处理心跳或重复累计时长。修改这些流程时优先保证数据库层幂等，并明确是否需要共享缓存/锁。

## 数据访问

- 简单 CRUD 沿用 MyBatis-Plus Service/Mapper。
- 统计、分页和批量查询沿用 XML Mapper，并同步维护投影类型。
- 避免逐条查询造成 N+1；优先使用已有 `chunk(s)`、分组查询或批量接口。

## 验证

```powershell
.\mvnw.cmd -pl playedu-course -am test
.\mvnw.cmd -pl playedu-api -am test
```

重点验证课程可见性、课时进度边界、重复心跳、完成事件、删除级联和学习统计。改变 Mapper SQL 时使用真实 MySQL 验证。
