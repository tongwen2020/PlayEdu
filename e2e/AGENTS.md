# EleadinEdu E2E 协作指南

## 适用范围

本文件适用于 `e2e/` 及全部子目录，并补充仓库根目录 `AGENTS.md`。

## 模块定位

本目录使用 Playwright 验证 Admin、PC 和 H5 的浏览器流程。默认配置连接已经启动、允许测试写入且可重置的验收环境；测试本身不负责启动主仓库服务。`playwright.h5-local.config.ts` 是例外：它启动 H5 Vite，并在浏览器层 Mock API 与 DPlayer，不需要数据库或测试账号。

当前套件覆盖登录/会话、核心导航、课程学习和考试基础流程，但不是所有后台题库、固定试卷、考试记录及权限分支的完整回归。新增业务能力时不要仅因已有同域用例就假设已覆盖。

## 结构

```text
e2e/
├── data/environment.ts       # URL、账号环境变量和 auth 文件路径
├── fixtures/                 # API、认证和 UI fixture
├── pages/                    # 页面对象
├── tests/setup/              # Admin/PC/H5 登录态生成
├── tests/admin/              # 管理端真实环境用例
├── tests/pc/                 # PC 真实环境用例
├── tests/h5/                 # H5 真实环境用例
├── tests/h5-local/           # H5 本地 Mock 全量用例
├── playwright.config.ts
└── playwright.h5-local.config.ts
```

## 环境与数据

- 从 `.env.example` 创建本地 `.env`；只使用专用测试管理员和学员账号。
- 默认验收地址为 Admin `9900`、PC `9800`、H5 `9801`、API `9700`，可通过环境变量覆盖。
- 依赖固定数据的课程/题库用例读取 `E2E_COURSE_ID`、`E2E_QUESTION_BANK_ID`；缺失时应明确跳过，不能假成功。
- 会写数据的用例使用唯一 `runId`，并在 `finally` 或 teardown 中按依赖反序清理。
- `.auth/*.json` 包含 Token；`.env`、`test-results/`、`playwright-report/`、截图、录像和 Trace 都不得提交。
- 不对开发者日常库或生产库运行破坏性测试。需要隔离环境时使用 `compose.e2e.yml`。

## 编写规则

- 从用户可见角色、名称、文本和结果断言，避免依赖易变的 DOM 层级、生成类名或任意等待。
- 优先使用 `getByRole`、`getByLabel`、`getByText` 和稳定页面对象；只有无语义定位方式时才补 `data-testid`。
- 不使用固定 `waitForTimeout` 掩盖竞态；等待导航、响应、可见状态或明确业务结果。
- Setup 只负责生成登录态；业务前置数据在 fixture 或测试中显式准备。
- API fixture 通过真实响应判断成功并保留清理所需 ID，不记录 Token、密码或完整敏感响应。
- 串行只用于确有共享状态的场景；默认保持配置中的并行执行能力。

## 当前考试能力边界

- PC 可验证开放题库练习、已发布客观题固定试卷、自动判分、考试记录和本人答题快照。
- Admin 可验证题库/试卷管理，以及从学员列表查看指定学员考试记录；后者使用 `user-learn` 权限。
- H5 当前只验证开放题库练习，不应编写依赖正式试卷入口的 H5 用例。
- 正式试卷用例必须准备已发布、`current_version > 0` 且不含主观题的试卷；同时覆盖停用/版本更新、重复提交和记录隔离。

## 命令与验证

```powershell
cd e2e
pnpm install --frozen-lockfile
pnpm install:browsers
pnpm typecheck
pnpm test:smoke
pnpm test:admin
pnpm test:pc
pnpm test:h5
pnpm test:h5:local
```

按改动范围运行最小充分集合。修改共享 fixture、认证或环境配置时至少执行 `pnpm typecheck` 和所有受影响项目；修改 H5 Mock 契约时运行 `pnpm test:h5:local`；修改真实后端契约时优先运行对应真实环境项目。

测试失败时保留报告用于本地诊断，但交付前不要提交生成物。交付说明需列出实际运行的项目、跳过原因、所用前置数据类型和仍未覆盖的风险。
