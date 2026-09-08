import "dotenv/config";
import { defineConfig, devices } from "@playwright/test";
import { authFiles, urls } from "./data/environment";

export default defineConfig({
  testDir: "./tests",
  outputDir: "test-results",
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  workers: process.env.E2E_WORKERS
    ? Number(process.env.E2E_WORKERS)
    : process.env.CI
      ? 2
      : undefined,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [["line"], ["html", { open: "never" }], ["junit", { outputFile: "test-results/junit.xml" }]]
    : [["list"], ["html", { open: "never" }]],
  use: {
    actionTimeout: 10_000,
    navigationTimeout: 20_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    locale: "zh-CN",
    timezoneId: "Asia/Shanghai",
  },
  projects: [
    { name: "admin-auth", testMatch: /admin\.setup\.ts/ },
    { name: "pc-auth", testMatch: /pc\.setup\.ts/ },
    { name: "h5-auth", testMatch: /h5\.setup\.ts/ },
    {
      name: "admin",
      testDir: "./tests/admin",
      dependencies: ["admin-auth"],
      use: {
        ...devices["Desktop Chrome"],
        baseURL: urls.admin,
        storageState: authFiles.admin,
      },
    },
    {
      name: "pc",
      testDir: "./tests/pc",
      dependencies: ["pc-auth"],
      use: {
        ...devices["Desktop Chrome"],
        baseURL: urls.pc,
        storageState: authFiles.pc,
      },
    },
    {
      name: "h5",
      testDir: "./tests/h5",
      dependencies: ["h5-auth"],
      use: {
        ...devices["Pixel 5"],
        baseURL: urls.h5,
        storageState: authFiles.h5,
      },
    },
  ],
});
