import { test, expect } from "./h5.fixture";

test.use({ authenticated: false });

test("H5 登录校验并建立学员会话", async ({ page, apiCalls }) => {
  await page.goto("/login");

  const loginButton = page.getByRole("button", { name: /登\s*录/ });
  await expect(loginButton).toBeDisabled();

  await page.getByPlaceholder("请输入邮箱或UID").fill("student@example.com");
  await expect(loginButton).toBeDisabled();
  await page.getByPlaceholder("请输入密码").fill("e2e-password");
  await expect(loginButton).toBeEnabled();
  await loginButton.click();

  await expect(page).toHaveURL(/\/member$/);
  await expect(page.getByText("今日学习", { exact: true })).toBeVisible();
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("playedu-h5-token")))
    .toBe("h5-e2e-token");

  const loginCall = apiCalls.find(
    (call) => call.pathname === "/api/v1/auth/login/password"
  );
  expect(loginCall?.body).toEqual({
    email: "student@example.com",
    password: "e2e-password",
  });
});

test("无 Token 访问业务页会回到登录页", async ({ page }) => {
  await page.goto("/exam");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText("学员登录", { exact: true })).toBeVisible();
});

test.describe("LDAP 登录", () => {
  test.use({ ldapEnabled: true });

  test("系统开启 LDAP 时使用 LDAP 登录接口", async ({ page, apiCalls }) => {
    await page.goto("/login");
    await page.getByPlaceholder("请输入邮箱或UID").fill("ldap-student");
    await page.getByPlaceholder("请输入密码").fill("ldap-password");
    await page.getByRole("button", { name: /登\s*录/ }).click();

    await expect(page).toHaveURL(/\/member$/);
    expect(
      apiCalls.some((call) => call.pathname === "/api/v1/auth/login/ldap")
    ).toBe(true);
    expect(
      apiCalls.some((call) => call.pathname === "/api/v1/auth/login/password")
    ).toBe(false);
  });
});
