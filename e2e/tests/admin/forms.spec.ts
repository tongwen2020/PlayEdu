import { test, expect } from "../../fixtures/ui.fixture";

test("部门页面能够打开新建部门表单", async ({ page }) => {
  await page.goto("/department");
  await page.getByText("新建部门", { exact: true }).click();
  await expect(page.getByText("新建部门", { exact: true }).last()).toBeVisible();
  await expect(page.getByPlaceholder("请输入部门名称")).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click();
});

test("学员页面能够打开新增学员表单", async ({ page }) => {
  await page.goto("/member/index");
  await page.getByText("添加学员", { exact: true }).click();
  await expect(page.getByText("添加学员", { exact: true }).last()).toBeVisible();
  await expect(page.getByPlaceholder("请填写学员姓名")).toBeVisible();
  await expect(page.getByPlaceholder("请输入学员登录邮箱")).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click();
});

test("课程页面能够打开新建课程表单", async ({ page }) => {
  await page.goto("/course");
  await page.getByText("新建课程", { exact: true }).click();
  await expect(page.getByText("新建课程", { exact: true }).last()).toBeVisible();
  await expect(page.getByPlaceholder("请输入课程名称")).toBeVisible();
  await page.getByRole("button", { name: "取消" }).click();
});
