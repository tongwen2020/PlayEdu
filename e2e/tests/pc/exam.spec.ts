import { test, expect } from "../../fixtures/ui.fixture";
import { fixtureIds } from "../../data/environment";

test("@smoke PC 学员可以进入考试中心并切换作答记录", async ({ page }) => {
  await page.goto("/exam");
  await expect(page.getByRole("heading", { name: "考试中心" })).toBeVisible();
  await page.getByRole("tab", { name: "我的作答记录" }).click();
  await expect(page.getByRole("tab", { name: "我的作答记录" })).toHaveAttribute("aria-selected", "true");
});

test("PC 学员可以进入预置题库练习", async ({ page }) => {
  test.skip(!fixtureIds.questionBankId, "Set E2E_QUESTION_BANK_ID to run the question practice case.");
  await page.goto(`/exam/practice/${fixtureIds.questionBankId}`);
  await expect(page).not.toHaveURL(/\/login/);
  if (fixtureIds.questionBankName) await expect(page.getByText(fixtureIds.questionBankName, { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "提交答案" }).first()).toBeVisible();
});
