import { test, expect } from "../../fixtures/ui.fixture";
import { fixtureIds } from "../../data/environment";

test("@smoke PC 学员首页展示学习概况", async ({ page }) => {
  await page.goto("/");
  await expect(page).not.toHaveURL(/\/login/);
  await expect(page.getByText("课程进度", { exact: true })).toBeVisible();
  await expect(page.getByText("学习时长", { exact: true })).toBeVisible();
});

test("PC 学员可以查看预置课程的目录和附件", async ({ page }) => {
  test.skip(!fixtureIds.courseId, "Set E2E_COURSE_ID to run the stable course regression case.");
  await page.goto(`/course/${fixtureIds.courseId}`);
  await expect(page).not.toHaveURL(/\/login/);
  if (fixtureIds.courseTitle) await expect(page.getByText(fixtureIds.courseTitle, { exact: true })).toBeVisible();
  await expect(page.getByText("课程目录", { exact: true })).toBeVisible();
  await expect(page.getByText("课程附件", { exact: true })).toBeVisible();
});
