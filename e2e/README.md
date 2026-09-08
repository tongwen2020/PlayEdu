# PlayEdu Playwright E2E

该目录保存 Admin、PC 和 H5 三端的持续回归验收用例。测试连接一个已经启动的、可重置的验收环境；Playwright 本身不会启动或修改开发环境。

## 首次运行

```powershell
cd e2e
Copy-Item .env.example .env
# 编辑 .env，填写专用管理员和学员账号
pnpm install --frozen-lockfile
pnpm install:browsers
pnpm test:smoke
pnpm typecheck
```

需要独立本地环境时，可以从本目录启动专用 Compose。它使用独立项目名和数据库卷，不应连接开发或生产数据库：

```powershell
docker compose -f compose.e2e.yml up -d --build
```

首次启动后，先通过管理端创建专用学员账号，并把账号写入本地 `.env`。若要运行课程和题库用例，还需要准备对该学员可见的固定课程及开放练习题库。

如果尚未生成 `pnpm-lock.yaml`，第一次执行 `pnpm install`，提交生成的锁文件；之后 CI 使用 `pnpm install --frozen-lockfile`。

默认地址与根目录 Compose 暴露端口一致：Admin `9900`、PC `9800`、H5 `9801`、API `9700`。可以通过 `.env` 覆盖。

## 用例分层

- `@smoke`：登录、会话、主要页面等每次提交执行的快速用例。
- 无标签用例：完整持续回归，包括表单交互和依赖预置数据的课程、题库场景。
- `E2E_COURSE_ID`、`E2E_QUESTION_BANK_ID` 未配置时，对应稳定数据用例会明确跳过。

常用命令：

```powershell
pnpm test
pnpm test:admin
pnpm test:pc
pnpm test:h5
pnpm test:headed
pnpm typecheck
pnpm report
```

## 数据和安全

- `.auth/*.json` 是运行时生成的 localStorage 登录态，包含 Token，不得提交。
- `test-results/` 保存失败截图、录像和 Trace；`playwright-report/` 保存 HTML 报告。
- `.env` 不提交，只提交 `.env.example`。
- 建议给固定验收课程和题库使用独立命名前缀，并由验收环境初始化脚本维护。
- 会写数据的后续用例应使用唯一 `runId` 创建数据，并在 `finally` 或 teardown 中反向清理。

## CI 建议

1. 启动隔离的 MySQL、API 和三个前端。
2. 等待四个 HTTP 地址健康。
3. 在 `e2e/` 安装依赖和 Chromium。
4. PR 执行 `pnpm test:smoke`，夜间执行 `pnpm test`。
5. 始终上传 `playwright-report/` 和 `test-results/`，即使测试失败。
