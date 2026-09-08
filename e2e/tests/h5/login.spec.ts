import { test, expect } from "../../fixtures/ui.fixture";

test.use({ storageState: { cookies: [], origins: [] } });

test("@smoke H5 学员登录按钮随必填信息启用", async ({ page }) => {
  await page.goto("/login");
  const button = page.getByRole("button", { name: /登\s*录/ });
  await expect(button).toBeDisabled();
  await page.getByPlaceholder("请输入邮箱或UID").fill("acceptance-student");
  await expect(button).toBeDisabled();
  await page.getByPlaceholder("请输入密码").fill("acceptance-password");
  await expect(button).toBeEnabled();
});
