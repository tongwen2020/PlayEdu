import type { Page } from "@playwright/test";
import { test, expect } from "./h5.fixture";

async function openSettings(page: Page) {
  await page.getByAltText("更多设置").click();
}

test("个人中心展示学习统计并切换部门", async ({ page, apiCalls }) => {
  await page.goto("/member");

  await expect(page.getByText("移动端学员", { exact: true })).toBeVisible();
  await expect(page.getByText("今日学习", { exact: true })).toBeVisible();
  await expect(page.getByText("累计学习", { exact: true })).toBeVisible();
  await expect(page.getByText("研发中心", { exact: true }).first()).toBeVisible();

  await openSettings(page);
  await page.getByText("切换部门", { exact: true }).click();
  await expect(page).toHaveURL(/\/change-department$/);
  await page.getByText("产品中心", { exact: true }).click();
  await expect(page).toHaveURL(/\/member$/);
  await expect(page.getByText("产品中心", { exact: true }).first()).toBeVisible();
  await expect
    .poll(() =>
      apiCalls.some(
        (call) =>
          call.pathname === "/api/v1/user/courses" &&
          call.query.dep_id === "2"
      )
    )
    .toBe(true);
});

test("学员可以修改密码", async ({ page, apiCalls }) => {
  await page.goto("/member");
  await openSettings(page);
  await page.getByText("修改密码", { exact: true }).click();

  await page.getByPlaceholder("请输入原密码").fill("old-password");
  await page.getByPlaceholder("请输入新密码").fill("new-password");
  await page.getByPlaceholder("请再次输入新密码").fill("new-password");
  await page.getByRole("button", { name: "确认修改" }).click();

  await expect
    .poll(() =>
      apiCalls.find((call) => call.pathname === "/api/v1/user/password")?.body
    )
    .toEqual({ old_password: "old-password", new_password: "new-password" });
});

test("学员可以更换头像", async ({ page, apiCalls }) => {
  await page.goto("/member");
  await openSettings(page);

  await page.locator('input[type="file"]').setInputFiles({
    name: "avatar.png",
    mimeType: "image/png",
    buffer: Buffer.from("mock-avatar"),
  });

  await expect(page.getByText("头像更换成功", { exact: true })).toBeVisible();
  expect(
    apiCalls.some((call) => call.pathname === "/api/v1/user/avatar")
  ).toBe(true);
});

test("退出登录会清理会话并返回登录页", async ({ page }) => {
  await page.goto("/member");
  await openSettings(page);
  await page.getByText("退出登录", { exact: true }).click();

  await expect(page).toHaveURL(/\/login$/);
  await expect
    .poll(() => page.evaluate(() => localStorage.getItem("playedu-h5-token")))
    .toBeNull();
});
