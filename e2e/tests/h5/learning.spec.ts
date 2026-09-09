import { test, expect } from "../../fixtures/ui.fixture";
import { fixtureIds } from "../../data/environment";

test("@smoke H5 学员中心展示学习统计和退出入口", async ({ page }) => {
  await page.goto("/member");
  await expect(page).not.toHaveURL(/\/login/);
  await expect(page.getByText("今日学习", { exact: true })).toBeVisible();
  await expect(page.getByText("累计学习", { exact: true })).toBeVisible();
  await expect(page.getByText("退出登录", { exact: true })).toBeVisible();
});

test("@smoke H5 学员可以进入最近学习页面", async ({ page }) => {
  await page.goto("/study");
  await expect(page.getByText("最近学习", { exact: true })).toBeVisible();
});

test("H5 学员可以查看预置课程及目录", async ({ page }) => {
  test.skip(!fixtureIds.courseId, "Set E2E_COURSE_ID to run the stable course regression case.");
  await page.goto(`/course/${fixtureIds.courseId}`);
  await expect(page).not.toHaveURL(/\/login/);
  if (fixtureIds.courseTitle) await expect(page.getByText(fixtureIds.courseTitle, { exact: true })).toBeVisible();
  await expect(page.getByText("课程目录", { exact: true })).toBeVisible();
});
