import { test, expect } from "../../fixtures/ui.fixture";

test.use({ storageState: { cookies: [], origins: [] } });

test("@smoke 后台登录页校验必填账号和密码", async ({ page }) => {
  await page.goto("/login");
  await page.getByRole("button", { name: "立即登录" }).click();
  await expect(page.getByText("请输入管理员邮箱账号")).toBeVisible();

  await page.getByPlaceholder("请输入管理员邮箱账号").fill("acceptance@example.com");
  await page.getByRole("button", { name: "立即登录" }).click();
  await expect(page.getByText("请输入密码")).toBeVisible();
});
