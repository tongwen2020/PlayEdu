import { test, expect } from "../../fixtures/ui.fixture";

test("@smoke 管理员进入仪表盘并展示核心指标", async ({ page }) => {
  await page.goto("/");
  await expect(page).not.toHaveURL(/\/login/);
  await expect(page.getByText("快捷操作")).toBeVisible();
  await expect(page.getByText("总学员数")).toBeVisible();
  await expect(page.getByText("资源统计")).toBeVisible();
});

test("@smoke 管理端核心业务页面可访问", async ({ page }) => {
  const pages = [
    { path: "/course", landmark: "新建课程" },
    { path: "/member/index", landmark: "添加学员" },
    { path: "/department", landmark: "新建部门" },
    { path: "/question-bank", landmark: "试题库" },
    { path: "/exam-paper", landmark: "试卷库" },
  ];

  for (const item of pages) {
    await test.step(item.path, async () => {
      await page.goto(item.path);
      await expect(page).not.toHaveURL(/\/login/);
      await expect(page.getByText(item.landmark, { exact: true }).first()).toBeVisible();
    });
  }
});
