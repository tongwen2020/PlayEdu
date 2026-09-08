# PlayEdu PC 学员端协作指南

## 适用范围

本文件适用于 `playedu-pc/` 目录及其全部子目录。它面向在本模块中工作的 Codex/开发人员，用于说明项目结构、运行方式、实现约束和验证要求。若更深层目录后续增加 `AGENTS.md`，应同时遵守距离目标文件最近的规则。

## 项目定位

`playedu-pc` 是 PlayEdu 企业培训系统的 PC 学员端，负责登录、课程浏览、课程学习、视频播放、最近学习以及考试中心题库练习。它只调用学员 API（`/api/v1/**`），不直接访问管理后台 API、数据库或对象存储。

当前考试中心已经覆盖后端现有的学员练习能力：开放题库、客观题作答、即时判分、答案解析和个人作答记录。规划中的正式考试组织、限时答卷、人工阅卷和成绩发布尚需对应后端接口支持，前端不得用静态假数据冒充这些能力。

## 技术基线

| 项目 | 当前配置 |
| --- | --- |
| UI 框架 | React 18、Ant Design 5 |
| 语言 | TypeScript 4.9、SCSS Modules |
| 路由 | React Router 6 |
| 状态管理 | Redux Toolkit、React Redux |
| HTTP | Axios |
| 构建 | Vite 4、SWC、pnpm |
| 兼容构建 | `@vitejs/plugin-legacy`，最低兼容配置包含 Chrome 52 |
| 测试 | Vitest、jsdom、Testing Library、user-event |

开发服务器默认监听 `0.0.0.0:9797`。API 根地址由 `VITE_APP_URL` 控制；生产容器通常设置为 `/api/`，再由 Nginx 转发到 Spring Boot 服务。

## 目录地图

```text
playedu-pc/
├── public/                    # 静态公共资源
├── src/
│   ├── api/                   # 按业务域封装学员端 API
│   │   └── internal/          # Axios 客户端和统一拦截器
│   ├── assets/                # Logo、图片、字体和 iconfont
│   ├── compenents/            # 公共组件；历史目录名拼写如此，不要擅自改名
│   ├── pages/                 # 页面和布局
│   │   ├── course/            # 课程详情与播放
│   │   ├── exam/              # 考试中心与在线练习
│   │   ├── index/             # 首页
│   │   ├── init/              # 系统配置和登录状态初始化
│   │   ├── latest-learn/      # 最近学习
│   │   ├── layouts/           # 不同头部/页脚组合的路由布局
│   │   └── login/             # 登录
│   ├── routes/                # React Router 路由表
│   ├── store/                 # 登录用户与系统配置 Redux Slice
│   ├── test/                  # 页面测试公共设置
│   ├── utils/                 # Token、部门、格式化和通用工具
│   ├── App.tsx
│   └── main.tsx
├── package.json
├── pnpm-lock.yaml
├── vite.config.ts
└── tsconfig.json
```

## 启动与鉴权流程

应用入口为 `src/main.tsx`，在 Redux、Ant Design 中文环境和 `BrowserRouter` 中渲染 `App`。

路由加载前，`src/routes/index.tsx` 根据本地 Token 决定初始化内容：

1. 无 Token 时只请求 `/api/v1/system/config`。
2. 有 Token 时同时请求系统配置和 `/api/v1/user/detail`。
3. `InitPage` 将配置及登录用户写入 Redux，再渲染子路由。
4. `PrivateRoute` 对受保护页面再次检查 Token，无 Token 时跳转 `/login`。

Token 保存在 `localStorage` 的 `playedu-frontend-token`，请求头使用 `Authorization: Bearer <token>`。当前部门 ID 和名称也保存在 `localStorage`。不要在新页面中自行实现另一套登录态或部门状态。

## API 约定

- 所有请求通过 `src/api/internal/httpClient.ts` 的共享客户端发送。
- API 文件按领域导出，并在 `src/api/index.ts` 统一暴露。
- 服务端统一响应形如 `{ code, data, msg }`；`code === 0` 表示成功。
- 401 会清理登录数据并跳转登录页；403、404、500 会进入统一错误页。
- 新增接口必须使用 `/api/v1/**` 学员路径，不可从 PC 端调用 `/backend/v1/**`。
- 请求和响应应声明 TypeScript 类型；不要在新增业务代码中无理由扩散 `any`。
- 不在日志、错误提示、测试快照或源码中输出 Token、密码及其他敏感数据。

网络无响应时 `error.response` 可能为空。若修改共享拦截器，应同时覆盖断网、超时和服务端无响应场景，避免错误处理本身再次抛异常。

## 路由与布局

路由集中在 `src/routes/index.tsx`，业务页面使用懒加载。新增页面时选择现有布局：

- `WithHeaderWithFooter`：首页、课程、最近学习、考试中心等普通页面。
- `WithHeaderWithoutFooter`：登录等不需要页脚的页面。
- `WithoutHeaderWithoutFooter`：视频播放、在线答题、错误页等沉浸式页面。

新增一级入口时同步检查头部导航及选中状态。嵌套路由应使用前缀判断保持一级导航高亮，不能只比较完整路径。

## 主要业务页面

### 课程学习

- 首页展示当前部门可见课程和学习概况。
- 课程详情展示章节、课时、附件及学习进度。
- 视频播放页获取播放地址，上报播放进度和在线心跳。
- 最近学习页聚合上次学习课时和课程完成度。

课程权限、完成条件和进度以服务端为准；前端进度显示不应替代后端校验。

### 考试中心

考试中心接口封装在 `src/api/exam.ts`，当前调用：

| 功能 | 接口 |
| --- | --- |
| 开放题库 | `POST /api/v1/question-bank/banks/list` |
| 练习题列表 | `POST /api/v1/question-bank/questions/list` |
| 提交答案 | `POST /api/v1/question-bank/practice/submit` |
| 作答记录 | `POST /api/v1/question-bank/practice/history` |

实现和修改时遵守以下约束：

- 学员题目响应不应包含标准答案、解析或后台评分规则；提交后才能展示服务端返回的答案与解析。
- 当前自动练习支持单选、多选和判断题；简答题依赖人工评分，不在该流程展示。
- 提交时使用 8～64 位请求标识实现幂等；同一次提交重试必须保持同一业务语义。
- 服务端判分是唯一可信结果，前端不可自行计算正确与否。
- 已提交答案不可在本地改写；离开页面前提示尚未提交的答案不会保存。
- 题号、完成数和分页均以实际接口数据为准，并覆盖加载、空态和失败状态。

## 状态与组件规则

- 跨页面共享的登录用户、部门和系统配置放 Redux；单页请求结果、表单和交互状态优先保留在页面组件中。
- 优先复用 Ant Design 的 Button、Modal、Tabs、Pagination、Skeleton、Empty、Radio、Checkbox、Progress 等语义组件。
- 通用组件放入历史目录 `src/compenents/`；只被单页使用的组件留在对应页面目录，避免过早抽象。
- 异步操作必须有 loading/disabled 防重复状态，并处理成功、失败、空数据和卸载后的页面行为。
- 日期、时长、UUID 等优先复用 `src/utils/index.ts`，避免各页面重复实现。

## 样式与可访问性

- 页面样式使用同目录 `*.module.scss`，通过 CSS Modules 引用；只将真正全局的规则放入 `index.scss` 或 `App.scss`。
- 延续现有品牌主色 `#ff4d4f`、1200px PC 内容宽度和 Ant Design 主题，不随意重做全站视觉系统。
- 正文和常用控件文字保持可读，交互状态不仅依赖颜色表达。
- 可点击非按钮元素应改用语义按钮/链接，或补足键盘访问和可访问名称。
- 图片提供有意义的 `alt`；纯装饰图片使用空 `alt`。
- 页面应至少在常见桌面宽度工作；修改已有响应式页面时同步检查其断点，不引入横向溢出。
- 保留项目 Logo 和 README 所述版权标识，不得在无明确授权时删除或替换。

## 编码约束

1. 开始修改前运行 `git status --short`，保留用户已有修改并避免覆盖无关文件。
2. 使用 `rg`/`rg --files` 查找现有路由、接口、类型和相似页面，优先延续已有模式。
3. 保持严格 TypeScript 可编译；新增代码避免非必要的类型断言和 `any`。
4. 不直接编辑 `dist/`、`node_modules/`、gzip 文件或其他生成物。
5. 依赖变更必须同步更新 `package.json` 和 `pnpm-lock.yaml`，不混用 npm/yarn 锁文件。
6. 不借普通功能开发顺带升级 React、Vite、Ant Design 或 TypeScript；老浏览器兼容升级需单独评估。
7. 不把后端尚未实现的规划功能做成前端静态演示后声称已完成。

## 本地开发

前置条件：Node.js 20、与锁文件兼容的 pnpm，以及可访问的 PlayEdu API。

```powershell
cd playedu-pc
pnpm install --frozen-lockfile
$env:VITE_APP_URL = "http://localhost:9898"
pnpm dev
```

默认访问地址为 `http://localhost:9797`。如通过 Docker/Nginx 访问，应遵循根项目的 `/api/` 反向代理配置。

## 构建与测试

按改动范围执行最小充分验证：

```powershell
# 页面和组件测试
pnpm test

# TypeScript 检查和生产构建
pnpm build

# 需要交互验证时启动本地页面
pnpm dev
```

验证要求：

- 只改 Markdown：运行 `git diff --check`，检查命令、路径和链接。
- 改 API 类型或封装：运行受影响页面测试和 `pnpm build`。
- 改页面交互：补充/更新 Testing Library 页面测试，并在浏览器验证主要用户流程。
- 改路由或鉴权：验证有 Token、无 Token、接口 401 和刷新深层路由。
- 改考试中心：至少覆盖题库列表、题型选择、提交判分、答案解析、题号切换和作答记录。
- 浏览器验证不得只确认页面能打开，还要验证关键按钮、导航、加载/空态和提交后的结果状态。

测试文件可与页面同目录命名为 `*.test.tsx`；全局 jsdom 兼容设置位于 `src/test/setup.ts`。测试应 mock HTTP 边界而不是复制页面内部实现，优先断言用户可见内容和可执行操作。

## 与后端联调

- 学员 API Controller 位于 `playedu-api/playedu-api/src/main/java/xyz/playedu/api/controller/frontend/`。
- 课程领域主要位于 `playedu-api/playedu-course/`。
- 考试题库、判分和练习记录位于 `playedu-api/playedu-exam/`，其 HTTP Controller 在应用模块。
- 修改接口契约时同步检查 PC、H5、Controller、请求 record/DTO 和后端 HTTP 测试。
- 联调必须验证登录失效、业务错误、空数据和版本冲突等非成功路径。

## 已知维护关注点

- `compenents`、`AutoScorllTop` 等历史拼写已被多处引用，除非任务明确要求整体迁移，否则不要局部重命名。
- 共享 Axios 错误处理对无 `response` 的网络异常不够健壮，相关改动需补回归测试。
- 根级 `index.scss` 含部分早期 Vite 模板样式和固定宽度规则，新页面应避免继续扩大不必要的全局影响。
- 当前 legacy 构建会产生较大的公共 chunk，并可能提示 Browserslist 数据过旧；不要把警告误报为构建失败，也不要在无测试的情况下直接升级兼容基线。
- PC 与 H5 存在重复业务逻辑；修改通用接口契约时应检查 H5，但未经任务授权不要顺带大范围抽包重构。

## 推荐阅读顺序

1. `package.json`、`vite.config.ts`、`tsconfig.json`。
2. `src/main.tsx`、`src/App.tsx`、`src/routes/index.tsx`。
3. `src/pages/init/index.tsx`、登录用户和系统配置 Redux Slice。
4. `src/api/internal/httpClient.ts` 与目标业务的 API 文件。
5. 目标页面、同目录样式和相邻页面测试。
6. 需要联调时再阅读后端对应的 frontend Controller 与领域 Service。

## 交付要求

交付时说明完成的用户功能、关键文件、实际运行的测试与构建结果，以及仍受后端能力限制的范围。不要声称未执行的验证已通过，也不要提交本地依赖缓存、构建产物、临时预览脚本或真实用户数据。
