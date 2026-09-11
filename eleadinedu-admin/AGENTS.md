# PlayEdu Admin 管理端协作指南

## 适用范围

本文件适用于 `eleadinedu-admin/` 及其全部子目录。它描述当前管理后台的实际架构、已接入功能、实现约束和验证方式；若更深层目录后续增加 `AGENTS.md`，同时遵守距离目标文件最近的规则。

## 项目定位

`eleadinedu-admin` 是 PlayEdu 企业培训与考试平台的管理后台，只调用 `/backend/v1/**` 管理端 API。当前覆盖概览、资源、课程、组织学员、学习数据、LDAP、管理员权限、系统配置和考试内容生产。

考试中心目前包含“试题库”和“试卷库”：管理员可以维护题库、题目及版本，开放学员题库练习，也可以编排、校验和发布固定试卷快照。仓库尚无考试活动、考生分配、考试时间/次数、人工阅卷工作台和成绩发布页面；不得把发布试卷描述成已经完成正式考试闭环。

## 技术基线

| 项目 | 当前实现 |
| --- | --- |
| UI | React 18、Ant Design 5、Ant Design Icons |
| 语言与样式 | TypeScript 4.9、Less Modules，少量 SCSS Modules |
| 路由 | React Router 6，`BrowserRouter` |
| 状态 | Redux Toolkit、React Redux |
| HTTP | Axios，共享拦截器 |
| 图表与工具 | ECharts、ahooks、dayjs/moment、xlsx |
| 构建 | Vite 4、SWC、pnpm、gzip |
| 测试 | Node.js `node:test` 导入契约测试；仓库级 Playwright E2E |

开发服务器监听 `0.0.0.0:3000`。API 根地址由 `VITE_APP_URL` 控制，空值表示同源；生产容器使用 `/api/` 并由 Nginx 转发到 Spring Boot。

## 目录地图

```text
eleadinedu-admin/
├── src/
│   ├── api/                       # 按业务域封装后台 API
│   │   └── internal/httpClient.ts # Axios、Token 和统一响应处理
│   ├── assets/                    # Logo、图标、图片和字体
│   ├── compenents/                # 公共组件；历史拼写，勿局部改名
│   ├── js/                        # 配置与 MinIO 分片上传
│   ├── pages/                     # 路由页面
│   │   ├── course/                # 课程、章节、课时、附件与学员进度
│   │   ├── department/            # 部门树与 LDAP 同步详情
│   │   ├── exam-paper/            # 固定试卷编排、发布和交换
│   │   ├── member/                # 学员、导入、学习与部门进度
│   │   ├── question-bank/         # 题库、分类、题目和版本
│   │   ├── resource/              # 分类、视频、图片和课件
│   │   └── system/                # 配置、管理人员、角色和日志
│   ├── routes/index.tsx           # 懒加载路由和启动初始化
│   ├── store/                     # 登录管理员与系统配置
│   └── main.tsx                   # 应用入口
├── tests/                         # 题库/试卷 JSON 导入边界测试
├── package.json
└── vite.config.ts
```

## 初始化、鉴权与权限

入口 `src/main.tsx` 在 Redux、Ant Design 中文环境和 `BrowserRouter` 中渲染应用。`src/routes/index.tsx` 根据本地 Token 初始化：有 Token 时同时请求系统配置和 `/backend/v1/auth/detail`，由 `InitPage` 写入 Redux；无 Token 时只渲染初始化出口，受保护路由由 `PrivateRoute` 跳转登录。

- Token 使用 `localStorage` 键 `eleadinedu-backend-token`，请求头为 `Authorization: Bearer <token>`。
- 401 会清理 Token 并跳转 `/login`；业务响应仅 `code === 0` 视为成功。
- 菜单由 `src/compenents/left-menu/index.tsx` 按管理员权限过滤，页面按钮仍需根据细粒度权限控制。
- 前端隐藏菜单或按钮不是安全边界；后端 Controller 的 `@BackendPermission` 必须执行最终授权。
- 新页面同时检查路由保护、菜单入口、菜单高亮和直接输入 URL 的无权限行为。

共享 HTTP 客户端在断网或超时时可能拿不到 `error.response`。修改拦截器时必须覆盖无响应异常，且不要重复弹出业务错误。

## 当前功能架构

### 培训运营

- 首页概览展示学员、课程、资源及学习相关指标。
- 资源中心维护资源分类、视频、图片和课件，支持普通/分片上传、编辑、批量删除和视频预览。
- 课程中心维护课程分类、可见部门/学员、章节、视频课时、附件、排序和课程学员学习记录。
- 学员与部门维护组织树、成员、批量导入、学习统计和进度清理；LDAP 页面展示同步记录与明细。
- 系统设置维护应用配置、管理人员、角色权限、密码和管理日志。

课程归属、资源引用、可见范围和学习进度以服务端校验为准。删除课程、资源、学员或部门时，不能只依赖前端确认框判断是否可删。

### 试题库

入口为 `/question-bank`，接口封装在 `src/api/question-bank.ts`：

- 维护题库名称、说明、启停状态和“开放学员练习”开关。
- 维护最多三级的题目分类，以及单选、多选、判断、简答四类题目。
- 题目包含编码、难度、题干、选项、标准答案、评分策略、建议分值、解析、标签和状态。
- 保存题目会生成新版本；支持版本查看、复制、启停、归档和仅删除未发布草稿。
- 支持 JSON 批量导入/导出；导入最多 1000 题，编码在题库内唯一，并在提交前校验分类、答案、评分规则、分值和重复项。

只有题库与题目均启用、且题库开放练习时，客观题才会出现在 PC/H5。简答题使用 `manual` 策略，不进入当前自动练习。

题库权限为 `question-bank-view`、`question-bank-edit`、`question-bank-export`。新增操作时同步检查后端权限常量、权限初始化和 Controller 注解。

### 试卷库

入口为 `/exam-paper`，接口封装在 `src/api/exam-paper.ts`：

- 维护独立的试卷分类、编码、名称、说明和标签。
- 固定试卷按“大题分区 → 题目项”编排，可从题库选择指定题目版本、排序并覆盖试卷实际分值。
- 发布前校验题目可用性、结构、题数、总分以及客观/主观题分；发布后生成不可变版本快照。
- 支持草稿、已发布、停用、归档状态，以及版本预览、复制指定版本、JSON 导入/导出。
- 导入最多 100 份，只创建新草稿，移除来源 ID/修订/版本和题目展示快照，保留题目 ID、题目版本和试卷分值。

试卷权限为 `exam-paper-view`、`exam-paper-edit`、`exam-paper-publish`、`exam-paper-export`。已发布或停用试卷再次编辑保存后会回到草稿；归档是终态。

固定试卷库当前不直接供 PC/H5 正式考试使用。学员端自动阅卷针对“开放题库整卷练习”，不是这里的发布试卷。

## 路由要点

| 路径 | 功能 |
| --- | --- |
| `/` | 首页概览 |
| `/resource-category`、`/videos`、`/images`、`/courseware` | 资源中心 |
| `/course`、`/course/user/:courseId` | 课程维护与学习记录 |
| `/question-bank`、`/exam-paper` | 考试内容生产 |
| `/member/index`、`/member/import`、`/member/learn` | 学员管理与学习数据 |
| `/department` | 部门与 LDAP 同步 |
| `/system/config/index`、`/system/administrator`、`/system/adminroles`、`/system/adminlog` | 系统设置 |
| `/change-password`、`/licensing` | 账号与许可 |
| `/login`、`/error` | 无侧栏页面 |

`/member` 使用 `KeepAlive` 嵌套路由。新增子页面时不要绕过现有布局和权限过滤；非菜单详情页要把选中状态映射回所属一级入口。

## API 与状态约定

- 业务 API 按领域放入 `src/api/`，并从 `src/api/index.ts` 统一导出。
- 沿用现有接口的 HTTP 方法和 `/backend/v1/**` 路径；题库与试卷是全 POST 风格，不要凭其他模块习惯改成 REST 动词。
- 服务端响应为 `{ code, data, msg }`。新增 API 应声明输入输出类型，避免继续扩散 `any`。
- 跨页面共享的管理员、权限和系统配置放 Redux；表格、筛选、弹窗、表单和请求状态保留在页面内。
- 异步写操作要有 loading/disabled 防重复状态，并覆盖成功、业务失败、网络失败和组件卸载。
- 版本/修订字段用于乐观并发控制；冲突时提示刷新，不允许前端静默覆盖。
- 不在日志、消息、测试快照或源码中暴露 Token、密码、对象存储密钥或预签名地址。

## 组件与样式

- 优先复用 Ant Design 的 Form、Table、Drawer、Modal、Tree、Upload、Result、Alert 等组件。
- 页面默认使用同目录 `*.module.less`；已有课程局部使用 `*.module.scss`，修改时延续所在目录，不做无关迁移。
- 保持现有主色 `#ff4d4f`、200px 侧栏和后台布局；处理小窗口溢出，但本项目不是 H5 管理端。
- `compenents`、`AutoScorllTop` 等历史拼写已被广泛引用，除非任务明确要求完整迁移，否则不要局部改名。
- 保留 Logo 和代码中的版权标识，不得在无授权时删除或替换。

## 开发与验证

```powershell
cd eleadinedu-admin
pnpm install --frozen-lockfile
$env:VITE_APP_URL = "http://localhost:9898"
pnpm dev
```

按改动范围执行最小充分验证：

```powershell
# TypeScript 检查与生产构建
pnpm build

# 题库和试卷导入契约
pnpm test:question-bank
pnpm test:exam-paper

# 从仓库 e2e/ 目录运行管理端验收
cd ../e2e
pnpm test:admin
```

- 只改文档：运行 `git diff --check`，核对路径、命令、路由、权限和功能边界。
- 改导入格式：运行对应 `node:test`，覆盖 BOM、数量上限、重复编码/题目、分类归属和无部分写入语义。
- 改题库/试卷页面：运行两个导入测试、`pnpm build`，并验证权限不足、加载/空态、保存失败、版本冲突和刷新。
- 改鉴权/路由/菜单：验证无 Token、401、直接访问受限 URL、菜单过滤和详情页高亮。
- 改资源上传：验证普通与分片上传、失败重试、合并、重复提交和对象存储异常。
- 改组织/课程删除：联调后端引用检查和跨模块清理，不能只确认界面提示。

仓库级 E2E 位于 `../e2e/`，需要可用的 API 与测试账号；当前管理端 E2E 覆盖登录、会话、核心导航和部分创建表单，不等同于题库/试卷全流程回归。

## 与后端及学员端联调

- 后台 Controller：`eleadinedu-api/eleadinedu-api/src/main/java/xyz/eleadinedu/api/controller/backend/`。
- 题库/试卷领域：`eleadinedu-api/eleadinedu-exam/`。
- 权限常量：`eleadinedu-api/eleadinedu-common/.../BPermissionConstant.java`；初始化：`eleadinedu-api/eleadinedu-system/.../AdminPermissionCheck.java`。
- 题库练习消费方：`eleadinedu-pc/src/pages/exam/`、`eleadinedu-h5/src/pages/exam/`。

修改考试内容契约时同步检查管理端类型、后端 record/Service/Controller、HTTP 测试和 PC/H5 的脱敏响应。后台可以看到标准答案与解析，学员题目列表不能看到；只有交卷结果才能返回评分详情。

## 推荐阅读顺序

1. `package.json`、`vite.config.ts`、`src/main.tsx`。
2. `src/routes/index.tsx`、`pages/init`、`compenents/left-menu` 和 `private-route`。
3. `api/internal/httpClient.ts`、登录用户与系统配置 Slice。
4. 目标页面及其 API、类型、样式和相邻组件。
5. 考试改动再阅读后端 Controller、`eleadinedu-exam` 和 PC/H5 消费方。

交付时说明影响的管理流程、关键文件、实际执行的测试/构建以及仍受后端能力限制的范围。不要提交 `dist/`、`node_modules/`、测试报告、真实导入数据或凭据，也不要声称未执行的验证已经通过。
