import { test, expect } from "../../fixtures/ui.fixture";

test.use({ storageState: { cookies: [], origins: [] } });

test("@smoke PC 学员登录页校验必填信息", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("button", { name: "立即登录" }).click();
  await expect(page.getByText("请输入邮箱或UID")).toBeVisible();
  await page.getByPlaceholder("请输入邮箱或UID").fill("acceptance-student");
  await page.getByRole("button", { name: "立即登录" }).click();
  await expect(page.getByText("请输入密码")).toBeVisible();
});
