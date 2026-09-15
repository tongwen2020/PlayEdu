# EleadinEdu 全仓库协作指南

## 适用范围

本文件适用于仓库根目录及全部子目录。进入 `eleadinedu-admin/`、`eleadinedu-api/`、`eleadinedu-pc/`、`eleadinedu-h5/` 或 `e2e/` 工作时，还必须读取该目录自己的 `AGENTS.md`；距离目标文件最近的规则优先，根规则仍然有效。

## 项目定位与当前范围

EleadinEdu 是前后端分离的企业培训与考试系统。当前仓库包含管理后台、PC 学员端、H5 学员端、Spring Boot API、MySQL 初始化/升级脚本、Docker 部署和跨端 Playwright 验收。

当前已实现的业务边界：

- 组织与身份：管理员、角色权限、学员、部门、LDAP 登录与同步。
- 培训学习：资源上传与分类、课程编排、可见范围、视频播放、附件、学习进度、心跳和统计。
- 题库练习：题库/题目版本、单选/多选/判断题整卷练习、幂等提交、自动判分和历史。
- 固定试卷：后台组卷、校验、发布不可变版本；PC 学员端参加只含客观题的已发布试卷，交卷后自动判分并生成不可变考试记录；学员本人和具备 `user-learn` 权限的管理员可查看答题快照。
- 当前不包含考试活动/场次、考生分配、开考与截止时间、次数限制、断点暂存、自动交卷、人工阅卷工作台、成绩审核/发布或 H5 正式试卷入口。不要把当前固定试卷自动测评描述成完整考试组织系统。

终端存在有意的不对称：PC 已接入正式试卷和考试记录，H5 目前只接入开放题库练习。跨端改动必须依据实际路由和 API 调用方判断，不能假设三端功能相同。

## 仓库地图

| 路径 | 职责 | 局部指南 |
| --- | --- | --- |
| `eleadinedu-admin/` | React 管理后台，使用 `/backend/v1/**` | `eleadinedu-admin/AGENTS.md` |
| `eleadinedu-pc/` | React PC 学员端，课程、题库练习、固定试卷与考试记录 | `eleadinedu-pc/AGENTS.md` |
| `eleadinedu-h5/` | React 移动学员端，课程学习与开放题库练习 | `eleadinedu-h5/AGENTS.md` |
| `eleadinedu-api/` | Java 17/Spring Boot 3.3 Maven 多模块后端 | `eleadinedu-api/AGENTS.md` 及各子模块指南 |
| `e2e/` | Admin、PC、H5 Playwright 验收与 H5 本地 Mock 回归 | `e2e/AGENTS.md` |
| `docker/`、`compose.yml`、`Dockerfile` | MySQL、Nginx、镜像与一体化部署 | 本文件 |
| `docs/`、根目录 `*.md` | 架构、测试跟踪和规划资料 | 本文件 |
| `outputs/` | 数据生成脚本和生成结果；非运行时业务代码 | 本文件 |

历史文档可能落后于代码。判断“当前已实现功能”时按以下优先级核对：可执行代码与测试 → SQL/迁移 → 当前 `AGENTS.md` → 项目概要/规划文档。规划文档中的建议接口和数据模型不能当作现状。

## 跨模块契约

- 管理端 API 固定使用 `/backend/v1/**`；学员端使用 `/api/v1/**`。前端不能跨边界调用另一侧接口。
- API 统一返回 `JsonResponse` 风格的 `{ code, data, msg }`；`code === 0` 表示业务成功。
- 管理员和学员 Token、上下文及本地存储键彼此独立，不新增第三套登录态。
- 后台菜单/按钮隐藏不是权限边界；后端 `@BackendPermission` 和上下文校验必须兜底。
- 题目列表和试卷详情在交卷前必须脱敏标准答案、解析和评分规则；评分结果/答题快照只能返回给答卷本人或有权管理员。
- 试卷发布版本、提交答案、评分结果和考试记录属于历史快照，不得因后续编辑题目或试卷而漂移。
- API 契约变化时同步检查 Controller、Request/record、领域 Service、SQL/迁移、Admin/PC/H5 调用方、后端 HTTP 测试和 E2E。

## 数据库与迁移

- 全新数据库基线维护在 `eleadinedu-api/sql/create_tables.sql`，默认数据维护在 `eleadinedu-api/sql/init_data.sql`。
- 已部署数据库通过唯一迁移标识升级；迁移必须可重复启动，并同时验证空库安装与旧库升级。
- 考试域当前包含题库、发布试卷版本、开放题库整卷答卷、固定试卷答卷和统一 `exam_records` 记录。变更任一表时检查历史回填、唯一约束、所有权校验和快照兼容性。
- 不把开发分支接入共享或生产数据库来试跑启动迁移；不提交真实密码、Token、JWT、LDAP 或对象存储密钥。

## 开发规则

1. 开始前运行 `git status --short`，保留用户已有修改；仓库中的 `.pnpm-store/` 可能出现本地缓存噪声，不编辑或提交它。
2. 使用 `rg`/`rg --files` 查找现有实现、调用方和测试，优先延续所在模块的模式。
3. 修改应保持在任务范围内；不要顺带升级框架、批量重命名历史拼写或格式化无关文件。
4. 不直接编辑 `node_modules/`、`dist/`、`target/`、Playwright 报告、失败录像、gzip 和第三方打包文件。
5. 前端依赖用 pnpm，更新 `package.json` 时同步锁文件；后端使用 Maven Wrapper。
6. 所有异步写操作处理重复提交和失败状态；考试交卷使用 8～64 位请求标识保证幂等，答案变化后才生成新标识。
7. 删除、归档、权限、上传和迁移属于高风险路径，要验证引用、一致性、鉴权及失败恢复。

## 本地端口与运行

| 服务 | 源码开发默认 | 根 Compose 宿主端口 |
| --- | ---: | ---: |
| Admin | `3000` | `9900` |
| PC | `9797` | `9800` |
| H5 | Vite 默认 `5173` | `9801` |
| API | `9898` | `9700` |
| MySQL | `3306` | `23307` |

根目录一体化启动：

```powershell
docker compose up -d --build
```

各模块的源码启动、环境变量和针对性命令见对应局部指南。

## 验证矩阵

只改 Markdown 时至少执行：

```powershell
git diff --check
```

代码改动按影响范围执行：

```powershell
# 后端（在 eleadinedu-api/）
.\mvnw.cmd test

# 各前端（在对应目录）
pnpm build

# PC 单元/页面测试
pnpm test

# Admin 导入契约测试
pnpm test:question-bank
pnpm test:exam-paper

# 跨端 E2E（在 e2e/）
pnpm typecheck
pnpm test:admin
pnpm test:pc
pnpm test:h5
pnpm test:h5:local
```

不要求每次运行全矩阵，但必须覆盖受影响边界。考试链路至少验证：发布状态与版本更新、主观题拒绝在线自动交卷、题目脱敏、未答计零、幂等重放、记录所有权、管理员权限、历史快照不漂移，以及 PC/H5 当前能力差异。

## 文档与交付

- `AGENTS.md` 描述当前可执行事实和协作约束；产品设想放规划文档，并明确“未实现”。
- 功能、接口、路由、表或验证命令变化时，同步更新最近层级的 `AGENTS.md`；跨模块边界变化时也更新本文件。
- 交付说明包含修改范围、关键文件、实际执行的验证和未验证风险。不要声称未运行的测试已通过，也不要把缓存、构建产物、真实账号或凭据作为交付物。
