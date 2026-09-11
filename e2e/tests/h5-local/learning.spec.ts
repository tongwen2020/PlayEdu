import { test, expect } from "./h5.fixture";

test("首页支持课程状态与分类筛选", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveTitle("PlayEdu 移动学习");
  await expect(page.getByText("信息安全入门", { exact: true })).toBeVisible();
  await expect(page.getByText("沟通协作技巧", { exact: true })).toBeVisible();

  await page.getByText("已学完", { exact: true }).click();
  await expect(page.getByText("信息安全入门", { exact: true })).toBeVisible();
  await expect(page.getByText("沟通协作技巧", { exact: true })).toBeHidden();

  await page.getByText("所有分类", { exact: true }).click();
  await page.getByText("安全培训", { exact: true }).click();
  await expect(page).toHaveURL(/cid=11/);
});

test("最近学习按时间展示课程并可进入详情", async ({ page }) => {
  await page.goto("/study");

  await expect(page.getByText("最近学习", { exact: true })).toBeVisible();
  await expect(page.getByText("今日", { exact: true })).toBeVisible();
  await page.getByText("沟通协作技巧", { exact: true }).click();
  await expect(page).toHaveURL(/\/course\/102$/);
});

test("课程详情展示目录、进度并通过授权接口下载附件", async ({ page, apiCalls }) => {
  await page.addInitScript(() => {
    const targetWindow = window as typeof window & { __openedUrl?: string };
    window.open = ((url?: string | URL) => {
      targetWindow.__openedUrl = String(url || "");
      return null;
    }) as typeof window.open;
  });
  await page.goto("/course/101");

  await expect(page.getByText("信息安全入门", { exact: true })).toBeVisible();
  await expect(page.getByText("已学完课时")).toContainText("1 / 2");
  await expect(page.getByText("安全意识基础", { exact: false })).toBeVisible();

  await page.getByText("课程附件", { exact: true }).click();
  await expect(page.getByText("安全学习手册.pdf", { exact: true })).toBeVisible();
  await page.getByText("下载", { exact: true }).click();

  await expect
    .poll(() =>
      apiCalls.some(
        (call) => call.pathname === "/api/v1/course/101/attach/701/download"
      )
    )
    .toBe(true);
  await expect
    .poll(() =>
      page.evaluate(
        () =>
          (window as typeof window & { __openedUrl?: string }).__openedUrl
      )
    )
    .toContain("attachment=security-guide");
});

test("视频课时续播并上报学习进度和心跳", async ({ page, apiCalls }) => {
  await page.goto("/course/101/hour/1001");

  await expect(page).toHaveTitle("安全意识基础");
  await expect(page.getByText("学习中", { exact: true })).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(() => Boolean((window as typeof window & { __lastPlayer?: unknown }).__lastPlayer))
    )
    .toBe(true);

  await page.evaluate(() => {
    const player = (window as typeof window & {
      __lastPlayer: {
        video: { currentTime: number };
        handlers: Record<string, () => void>;
      };
    }).__lastPlayer;
    player.video.currentTime = 23;
    player.handlers.timeupdate();
  });

  await expect
    .poll(() =>
      apiCalls.filter(
        (call) => call.pathname === "/api/v1/course/101/hour/1001/record"
      ).length
    )
    .toBe(1);
  expect(
    apiCalls.find(
      (call) => call.pathname === "/api/v1/course/101/hour/1001/record"
    )?.body
  ).toEqual({ duration: 23 });
  expect(
    apiCalls.some(
      (call) => call.pathname === "/api/v1/course/101/hour/1001/ping"
    )
  ).toBe(true);
});
