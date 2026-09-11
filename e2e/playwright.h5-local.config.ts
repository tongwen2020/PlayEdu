import { defineConfig, devices } from "@playwright/test";

const node = `"${process.execPath}"`;

export default defineConfig({
  testDir: "./tests/h5-local",
  outputDir: "test-results/h5-local",
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  timeout: 30_000,
  expect: { timeout: 8_000 },
  reporter: process.env.CI
    ? [["line"], ["html", { outputFolder: "playwright-report/h5-local", open: "never" }]]
    : [["list"], ["html", { outputFolder: "playwright-report/h5-local", open: "never" }]],
  use: {
    ...devices["Pixel 5"],
    baseURL: "http://127.0.0.1:9811",
    locale: "zh-CN",
    timezoneId: "Asia/Shanghai",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
  webServer: {
    command: `${node} ../playedu-h5/node_modules/vite/bin/vite.js ../playedu-h5 --host 127.0.0.1 --port 9811`,
    url: "http://127.0.0.1:9811/",
    cwd: process.cwd(),
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
