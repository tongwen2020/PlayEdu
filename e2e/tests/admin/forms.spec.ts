import { test, expect } from "../../fixtures/ui.fixture";

test("部门页面能够打开新建部门表单", async ({ page }) => {
  await page.goto("/department");
  await page.getByRole("button", { name: /新建部门/ }).click();
  const dialog = page.getByRole("dialog", { name: "新建部门" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByPlaceholder("请输入部门名称")).toBeVisible();
  await dialog.getByRole("button", { name: /取\s*消/ }).click();
});

test("学员页面能够打开新增学员表单", async ({ page }) => {
  await page.goto("/member/index");
  await page.getByRole("button", { name: /添加学员/ }).click();
  const dialog = page.getByRole("dialog", { name: "添加学员" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByPlaceholder("请填写学员姓名")).toBeVisible();
  await expect(dialog.getByPlaceholder("请输入学员登录邮箱")).toBeVisible();
  await dialog.getByRole("button", { name: /取\s*消/ }).click();
});

test("课程页面能够打开新建课程表单", async ({ page }) => {
  await page.goto("/course");
  await page.getByRole("button", { name: /新建课程/ }).click();
  const dialog = page.getByRole("dialog", { name: "新建课程" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByPlaceholder("请在此处输入课程名称")).toBeVisible();
  await dialog.getByRole("button", { name: /取\s*消/ }).click();
});
