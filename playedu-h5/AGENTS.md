# PlayEdu H5 学员端业务概要

## 适用范围

本文件适用于 `playedu-h5/` 及其全部子目录，用于帮助开发人员快速理解移动端学员业务、代码入口和实现边界。若更深层目录后续增加 `AGENTS.md`，同时遵守距离目标文件最近的规则。

## 模块定位

`playedu-h5` 是 PlayEdu 企业培训系统面向手机浏览器和微信内置浏览器的学员端。它只调用 `/api/v1/**` 学员 API，不直接访问管理后台 API、数据库或对象存储。

当前业务闭环为：学员登录 → 选择所属部门 → 浏览被分配的课程 → 查看目录或下载附件 → 观看视频并上报学习进度 → 在最近学习和个人中心查看学习情况；学员也可以进入考试中心选择开放题库、完成客观题练习并查看作答记录。

本模块当前不包含注册、找回密码、课程搜索、正式考试组织、限时答卷、人工阅卷、评论、收藏或付费能力。考试中心目前只支持后台明确开放的题库及单选、多选、判断题即时练习；不要用静态数据或纯前端逻辑将未接入的能力描述为可用功能。

## 技术基线

| 项目 | 当前实现 |
| --- | --- |
| UI | React 18、antd-mobile 5 |
| 语言与样式 | TypeScript 4.9、SCSS Modules |
| 路由 | React Router 6，`BrowserRouter` |
| 状态 | Redux Toolkit、React Redux |
| HTTP | Axios |
| 视频 | `public/js/DPlayer.min.js` 全局脚本 |
| 构建 | Vite 4、SWC、pnpm、gzip、legacy 构建 |
| 浏览器基线 | legacy target 包含 Chrome 52 |

Vite 开发服务器监听 `0.0.0.0`，端口使用 Vite 默认值。API 根地址由 `VITE_APP_URL` 控制；空值表示同源请求。

## 用户与业务流程

### 初始化和鉴权

应用入口为 `src/main.tsx`。`src/routes/index.tsx` 在模块加载时读取本地 Token，并在展示页面前完成初始化：

1. 无 Token 时请求 `/api/v1/system/config`，加载系统名称、PC/H5 地址和播放器配置。
2. 有 Token 时额外请求 `/api/v1/user/detail`，把用户、所属部门和资源地址写入 Redux。
3. `InitPage` 在非移动设备上、且系统配置了 PC 地址时跳转到 PC 学员端。
4. 受保护页面由 `PrivateRoute` 再次检查 Token，无 Token 则跳转 `/login`。

Token 使用 `localStorage` 键 `playedu-h5-token`，请求头格式为 `Authorization: Bearer <token>`。接口返回 401 时清理 Token 并回到登录页。登录支持普通密码和 LDAP；是否走 LDAP 由系统配置 `ldap-enabled` 决定。

### 部门上下文

一个学员可以属于多个部门，课程可见范围和个人学习统计均依赖当前部门：

- 当前部门 ID 存在 Redux，并通过 `playedu-h5-depatmentKey` 持久化。
- 当前部门名称通过 `playedu-h5-depatmentName` 持久化。
- 初次登录且没有已选部门时，默认使用接口返回的第一个部门。
- 切换部门后，首页课程和个人中心统计会按新的 `dep_id` 重新加载。

不要在单个页面中另建部门状态；新增依赖部门的请求应读取 `loginUser.value.currentDepId`，并处理部门 ID 为 `0` 的情况。

### 课程发现

首页 `/` 展示当前部门可见课程，并支持：

- 按树形课程分类筛选；分类来自 `/api/v1/category/all`。
- 按全部、必修、选修、已学完、未学完切换。
- 下拉刷新课程列表。
- 展示课程封面、必修/选修标识及学习进度。

课程筛选状态同步到 URL 查询参数 `cid`、`catName`、`tab`。必修/选修和完成状态目前由前端基于 `/api/v1/user/courses` 的完整返回集筛选；`progress >= 10000` 视为课程完成。

### 课程详情与附件

课程详情 `/course/:courseId` 展示课程名称、简介、必修属性、完成课时数、整体进度、章节和视频课时。无章节课程的课时位于 `hours[0]`，有章节课程则按章节 ID 从 `hours` 中取列表。

存在附件时显示“课程附件”页签。下载前先调用授权接口获取实际资源 URL：

- 普通浏览器直接打开资源地址。
- iOS 通过隐藏下载链接触发。
- 微信环境提示使用外部浏览器；现有实现还会复制并尝试打开地址。

资源 URL 必须使用接口返回的 `resource_url` 映射，不要自行拼接对象存储路径，也不要绕过附件下载接口的权限检查。

### 视频学习与进度

播放页 `/course/:courseId/hour/:hourId` 先获取课时详情和授权播放地址，再初始化全局 `window.DPlayer`：

- 未完成课时从服务端返回的 `finished_duration` 续播。
- 播放过程中约每前进 10 秒调用 `record` 记录播放位置，并调用 `ping` 上报在线心跳。
- 播放结束时强制上报，并展示“下一节”或“已学完最后一节”。
- 配置 `player-disabled-drag=1` 时，首次学习期间限制快进；是否完成和可拖动范围最终以服务端记录为准。
- 配置开启防录屏水印时，用学员姓名、邮箱、证件号替换水印模板占位符。
- 切换课时或返回前需要销毁旧播放器，避免重复事件监听和后台播放。

前端显示的进度不是判定学习完成的权威来源。课程进度字段为万分比：`10000` 表示 100%，展示时通常使用 `Math.floor(progress / 100)`；课时进度按 `finished_duration / total_duration` 计算。

### 最近学习

“学习”页 `/study` 调用 `/api/v1/user/latest-learn`，按课时记录更新时间分为“今日”“昨日”“更早”，并展示课程进度。点击课程进入课程详情，支持下拉刷新和空态。

### 考试中心

“考试”页 `/exam` 提供开放题库和个人作答记录：

- 题库列表只展示后台状态正常且开启练习的题库，并显示可练习客观题数量。
- 练习页 `/exam/practice/:bankId` 支持单选、多选和判断题，可通过上一题、下一题和答题卡切换。
- 学员提交单题后由服务端即时判分，并展示标准答案、得分和解析；提交后的答案不可修改。
- 每次提交使用 8～64 位请求标识保证幂等，同一道题因网络失败重试时复用原请求标识。
- 离开页面前提示未提交答案不会保存；已提交结果可在“我的作答”中分页查看。

题目列表响应不应包含标准答案和解析，前端也不得自行判分；只有提交接口的结果可以作为正确性依据。当前不展示简答题，因为它依赖人工评分能力。

### 个人中心

“我的”页 `/member` 展示：

- 头像、姓名和当前部门。
- 今日及累计学习时长。
- 已完成课时数、必修课完成数、选修课完成数。
- 切换部门、更换头像、修改密码和退出登录入口。

头像上传走 `PUT /api/v1/user/avatar`，成功后重新拉取用户详情。修改密码要求原密码和两次一致的新密码。退出登录当前只清理本地 Redux/Token/部门信息并跳转登录页，没有调用已封装的服务端 `logout` 接口；修改这段流程时要同时评估服务端会话失效要求。

## 路由地图

| 路径 | 页面 | 鉴权 | 底部导航 |
| --- | --- | --- | --- |
| `/login` | 普通密码或 LDAP 登录 | 否 | 否 |
| `/` | 课程首页 | 是 | 是 |
| `/study` | 最近学习 | 是 | 是 |
| `/exam` | 开放题库与个人作答记录 | 是 | 是 |
| `/member` | 个人中心与学习统计 | 是 | 是 |
| `/change-department` | 切换当前部门 | 是 | 否 |
| `/change-password` | 修改密码 | 是 | 否 |
| `/course/:courseId` | 课程详情、目录、附件 | 是 | 否 |
| `/course/:courseId/hour/:hourId` | 视频播放和课时切换 | 是 | 否 |
| `/exam/practice/:bankId` | 客观题练习、判分和解析 | 是 | 否 |

首页、学习、我的共用 `pages/layouts/with-footer`；其余页面使用 `without-footer`。新增一级底部入口时同步更新 `components/bar-footer` 的路由键和选中逻辑。

## API 清单

所有请求通过 `src/api/internal/httpClient.ts` 的共享客户端发送，并由 `src/api/index.ts` 统一导出。服务端统一响应形如 `{ code, data, msg }`，仅 `code === 0` 视为成功。

| 领域 | 方法与接口 | 用途 |
| --- | --- | --- |
| 系统 | `GET /api/v1/system/config` | 品牌、端地址、LDAP 和播放器配置 |
| 登录 | `POST /api/v1/auth/login/password` | 邮箱或 UID + 密码登录 |
| 登录 | `POST /api/v1/auth/login/ldap` | LDAP 用户名密码登录 |
| 登录 | `POST /api/v1/auth/logout` | 服务端退出；当前页面流程未调用 |
| 用户 | `GET /api/v1/user/detail` | 用户、部门和资源地址 |
| 用户 | `PUT /api/v1/user/password` | 修改密码 |
| 用户 | `PUT /api/v1/user/avatar` | 上传头像 |
| 课程 | `GET /api/v1/category/all` | 课程分类树 |
| 课程 | `GET /api/v1/user/courses` | 当前部门课程和学习统计 |
| 课程 | `GET /api/v1/user/latest-learn` | 最近学习记录 |
| 课程 | `GET /api/v1/course/:id` | 课程、章节、课时、附件和进度 |
| 课时 | `GET /api/v1/course/:courseId/hour/:hourId` | 当前课时和续播记录 |
| 课时 | `GET /api/v1/course/:courseId/hour/:hourId/play` | 授权播放地址 |
| 课时 | `POST /api/v1/course/:courseId/hour/:hourId/record` | 上报播放位置 |
| 课时 | `POST /api/v1/course/:courseId/hour/:hourId/ping` | 播放心跳 |
| 附件 | `GET /api/v1/course/:courseId/attach/:id/download` | 获取授权下载地址 |
| 考试 | `POST /api/v1/question-bank/banks/list` | 获取开放练习题库 |
| 考试 | `POST /api/v1/question-bank/questions/list` | 获取不含答案的客观题列表 |
| 考试 | `POST /api/v1/question-bank/practice/submit` | 幂等提交答案并获取判分解析 |
| 考试 | `POST /api/v1/question-bank/practice/history` | 分页获取个人作答记录 |

新增接口继续使用学员端 `/api/v1/**`，不得从 H5 调用 `/backend/v1/**`。请求和响应应补充 TypeScript 类型，避免继续扩大页面中的 `any`。

## 代码结构

```text
playedu-h5/
├── public/js/DPlayer.min.js       # 页面通过全局 window.DPlayer 使用
├── src/
│   ├── api/                       # 登录、系统、用户、课程 API
│   │   └── internal/httpClient.ts # Axios 实例、Token 与统一错误处理
│   ├── assets/                    # 图片、默认封面和 iconfont
│   ├── components/                # 底部栏、空态、鉴权等公共组件
│   ├── pages/                     # 按路由划分的业务页面
│   ├── routes/index.tsx           # 路由与启动初始化
│   ├── store/                     # 用户/部门和系统配置 Redux Slice
│   ├── utils/index.ts             # Token、部门、时间和终端判断
│   ├── playedu.d.ts               # 课程、课时、用户等全局类型
│   └── main.tsx                   # 实际应用入口
├── index.html
├── package.json
└── vite.config.ts
```

`src/index.tsx` 是遗留入口且未被 `index.html` 引用；不要误将其作为当前启动链路。`compenents` 是页面子目录中的历史拼写，除非进行完整迁移，否则不要局部改名造成引用断裂。

## 开发约束

- 跨页面共享的登录用户、部门和系统配置放 Redux；列表、表单、加载状态等页面数据保留在页面组件中。
- 优先复用 antd-mobile 的移动端组件，样式放同目录 `*.module.scss`；全局覆盖只放 `main.scss`。
- 保持现有品牌主色 `#ff4d4f`，并兼顾窄屏、安全区、微信内置浏览器和 iOS 下载行为。
- 异步请求必须有 loading/disabled 防重复状态，并处理失败、空数据和组件卸载或路由切换。
- 学习权限、课程可见范围、授权资源地址和完成状态均以服务端为准，不在前端伪造或越权推导。
- 不在日志、Toast、源码或构建产物中暴露 Token、密码、授权播放地址等敏感信息。
- 不直接编辑 `dist/`、`node_modules/`、gzip 文件或 `public/js/DPlayer.min.js` 等生成/第三方文件。
- 依赖变更同步更新 `package.json` 和 `pnpm-lock.yaml`，不要混用 npm/yarn 锁文件，也不要借普通业务改动升级技术基线。

## 已知维护关注点

- Axios 错误拦截器直接读取 `error.response.status`；断网、超时等无响应异常可能使错误处理再次抛错。修改共享客户端时需覆盖这些场景。
- `clearDepName()` 当前删除的键与 `setDepName()` 写入的键不一致，退出后可能残留部门名称；涉及登录态清理时应一并核对。
- 初始化请求失败时，懒加载 Promise 没有 resolve/reject，页面可能长期停留在 Loading；修改初始化流程时要提供可恢复的错误态。
- 视频播放器挂在全局 `window.player`，事件和实例生命周期必须谨慎管理。
- 项目当前没有自动化测试脚本；业务交互变更至少执行生产构建并手工验证受影响的移动端流程。

## 本地开发与验证

```powershell
cd playedu-h5
pnpm install --frozen-lockfile
$env:VITE_APP_URL = "http://localhost:9898"
pnpm dev
```

生产构建：

```powershell
pnpm build
```

按改动范围执行最小充分验证：

- 只改文档：运行 `git diff --check`，核对命令、路径、路由和接口。
- 改 API 或类型：运行 `pnpm build`，并联调成功、业务错误、401 和网络失败。
- 改登录或部门：验证普通/LDAP 登录、刷新恢复、无 Token、切换部门、退出后重新登录。
- 改课程列表：验证分类、五种状态筛选、URL 参数、下拉刷新、默认封面和空态。
- 改播放：在真机或移动端模拟器验证续播、10 秒进度上报、心跳、禁拖动、播放结束、下一课时和播放器销毁。
- 改附件：分别验证普通浏览器、iOS 和微信环境；不要只确认按钮可点击。
- 改考试中心：验证题库/历史空态、三种客观题、提交防重复、服务端判分、解析、答题卡、分页及未提交退出提示。

后端联调入口位于 `playedu-api/playedu-api/src/main/java/xyz/playedu/api/controller/frontend/`。修改接口契约时同步检查对应 Controller、DTO/record、PC 学员端调用方以及后端测试。

## 推荐阅读顺序

1. `package.json`、`vite.config.ts`、`index.html`。
2. `src/main.tsx`、`src/App.tsx`、`src/routes/index.tsx`、`src/pages/init/index.tsx`。
3. `src/api/internal/httpClient.ts` 与目标业务 API 文件。
4. `src/store/`、`src/utils/index.ts`、`src/playedu.d.ts`。
5. 目标页面及其同目录组件和样式。
6. 需要联调时再阅读后端对应的 frontend Controller 和领域 Service。

交付时说明影响的用户流程、关键文件、实际执行的验证，以及仍受后端或终端环境限制的范围。不要声称未执行的验证已经通过。
