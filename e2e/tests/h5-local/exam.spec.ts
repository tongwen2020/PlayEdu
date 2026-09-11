import { test, expect } from "./h5.fixture";

test("考试中心展示开放题库、作答记录和分页", async ({ page, apiCalls }) => {
  await page.goto("/exam");

  await expect(page.getByRole("heading", { name: "考试中心" })).toBeVisible();
  await expect(page.getByText("信息安全题库", { exact: true })).toBeVisible();
  await expect(page.getByText("3 道题", { exact: true })).toBeVisible();

  await page.getByText("我的作答", { exact: true }).click();
  await expect(
    page.getByText("收到可疑邮件时应该怎么做？", { exact: true })
  ).toBeVisible();
  await page.getByRole("button", { name: "下一页" }).click();
  await expect(page.getByText("2 / 2", { exact: true })).toBeVisible();
  await expect(page.getByText("第二页作答记录", { exact: true })).toBeVisible();

  expect(
    apiCalls.some(
      (call) =>
        call.pathname === "/api/v1/question-bank/practice/history" &&
        (call.body as { page?: number })?.page === 2
    )
  ).toBe(true);
});

test("完成单选、多选、判断题后展示服务端判分解析", async ({ page, apiCalls }) => {
  await page.goto("/exam");
  await page.getByRole("button", { name: "开始练习" }).click();
  await expect(page).toHaveURL(/\/exam\/practice\/8$/);

  await expect(
    page.getByRole("heading", { name: /收到可疑邮件时应该怎么做/ })
  ).toBeVisible();
  await page.getByText("报告安全团队", { exact: true }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await expect(page.getByText("回答正确", { exact: false })).toBeVisible();
  await expect(page.getByText("正确答案：", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "下一题" }).click();

  await expect(
    page.getByRole("heading", { name: /以下哪些属于强密码实践/ })
  ).toBeVisible();
  await page.getByText("使用足够长度", { exact: true }).click();
  await page.getByText("不同系统使用不同密码", { exact: true }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await expect(page.getByText("答案解析：", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "下一题" }).click();

  await expect(
    page.getByRole("heading", { name: /工作账号可以与他人共享/ })
  ).toBeVisible();
  await page.getByText("错误", { exact: true }).click();
  await page.getByRole("button", { name: "提交答案" }).click();
  await expect(page.getByText("账号仅限本人使用", { exact: false })).toBeVisible();
  await page.getByRole("button", { name: "完成练习" }).click();
  await expect(page).toHaveURL(/\/exam$/);

  const submissions = apiCalls.filter(
    (call) => call.pathname === "/api/v1/question-bank/practice/submit"
  );
  expect(submissions).toHaveLength(3);
  expect(submissions.map((call) => (call.body as { answer: unknown }).answer)).toEqual([
    { optionIds: ["b"] },
    { optionIds: ["a", "b"] },
    { value: false },
  ]);
  for (const submission of submissions) {
    expect((submission.body as { requestKey: string }).requestKey).toMatch(
      /^[a-f0-9-]{36}$/
    );
  }
});

test("退出练习前提示未提交答案", async ({ page }) => {
  await page.goto("/exam/practice/8");
  await page.getByText("点击链接确认", { exact: true }).click();
  await page.getByRole("button", { name: "退出练习" }).click();

  await expect(page.getByText("确认退出练习？", { exact: true })).toBeVisible();
  await expect(
    page.getByText("尚未提交的答案不会保存，已提交记录不受影响。", {
      exact: true,
    })
  ).toBeVisible();
  await page.getByText("继续答题", { exact: true }).click();
  await expect(page).toHaveURL(/\/exam\/practice\/8$/);
});
