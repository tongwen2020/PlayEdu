import { test as base, expect, type Page, type Route } from "@playwright/test";

export type ApiCall = {
  method: string;
  pathname: string;
  query: Record<string, string>;
  body: unknown;
};

type H5Fixtures = {
  authenticated: boolean;
  ldapEnabled: boolean;
  apiCalls: ApiCall[];
};

const user = {
  id: 7,
  name: "移动端学员",
  avatar: -1,
  credit1: 0,
  email: "student@example.com",
  create_city: "杭州",
  create_ip: "127.0.0.1",
  id_card: "330100199001010000",
  is_active: 1,
  is_lock: 0,
  is_set_password: 1,
  is_verify: 1,
  created_at: "2026-09-01T08:00:00",
  updated_at: "2026-09-09T08:00:00",
};

const departments = [
  { id: 1, name: "研发中心" },
  { id: 2, name: "产品中心" },
];

const courses = [
  {
    id: 101,
    title: "信息安全入门",
    thumb: -1,
    short_desc: "掌握日常工作中的信息安全基础知识。",
    is_required: 1,
    charge: 0,
    class_hour: 2,
  },
  {
    id: 102,
    title: "沟通协作技巧",
    thumb: -2,
    short_desc: "提升跨团队沟通效率。",
    is_required: 0,
    charge: 0,
    class_hour: 1,
  },
];

const hourRecords = {
  1001: {
    id: 1,
    user_id: 7,
    course_id: 101,
    hour_id: 1001,
    is_finished: 0,
    finished_duration: 12,
    real_duration: 12,
    total_duration: 60,
    finished_at: "",
    created_at: "2026-09-09T08:00:00",
    updated_at: "2026-09-09T08:00:00",
  },
};

const json = (route: Route, data: unknown) =>
  route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({ code: 0, data, msg: "" }),
  });

async function readBody(route: Route): Promise<unknown> {
  const request = route.request();
  if (!request.postData()) return undefined;
  try {
    return request.postDataJSON();
  } catch {
    return request.postData();
  }
}

async function mockApi(
  page: Page,
  apiCalls: ApiCall[],
  ldapEnabled: boolean
) {
  await page.route("**/js/DPlayer.min.js", (route) =>
    route.fulfill({
      contentType: "application/javascript",
      body: `
        window.DPlayer = function (options) {
          this.options = options;
          this.video = { currentTime: 0, duration: 60 };
          this.handlers = {};
          this.destroyed = false;
          this.on = (name, callback) => { this.handlers[name] = callback; };
          this.seek = (value) => { this.video.currentTime = value; };
          this.destroy = () => { this.destroyed = true; };
          window.__lastPlayer = this;
        };
      `,
    })
  );

  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;
    const body = await readBody(route);
    apiCalls.push({
      method: request.method(),
      pathname,
      query: Object.fromEntries(url.searchParams),
      body,
    });

    if (pathname === "/api/v1/system/config") {
      return json(route, {
        "ldap-enabled": ldapEnabled ? "1" : "0",
        "system-h5-url": "",
        "system-logo": "",
        "system-name": "PlayEdu 移动学习",
        "system-pc-url": "",
        "system-pc-index-footer-msg": "",
        "player-poster": "",
        "player-is-enabled-bullet-secret": "0",
        "player-disabled-drag": "0",
        "player-bullet-secret-text": "{name}-{email}-{idCard}",
        "player-bullet-secret-color": "red",
        "player-bullet-secret-opacity": "0.5",
        resource_url: {},
      });
    }

    if (pathname === "/api/v1/auth/login/password") {
      return json(route, { token: "h5-e2e-token" });
    }

    if (pathname === "/api/v1/auth/login/ldap") {
      return json(route, { token: "h5-ldap-e2e-token" });
    }

    if (pathname === "/api/v1/user/detail") {
      return json(route, { user, departments, resource_url: {} });
    }

    if (pathname === "/api/v1/category/all") {
      return json(route, {
        categories: {
          0: [{ id: 11, name: "安全培训" }],
        },
      });
    }

    if (pathname === "/api/v1/user/courses") {
      return json(route, {
        courses,
        learn_course_records: {
          101: { progress: 10000 },
          102: { progress: 3500 },
        },
        user_course_hour_count: { 101: 2, 102: 1 },
        resource_url: {},
        stats: {
          today_learn_duration: 3_600_000,
          learn_duration: 9_000_000,
          required_finished_hour_count: 2,
          nun_required_finished_hour_count: 0,
          required_hour_count: 2,
          nun_required_hour_count: 1,
          required_finished_course_count: 1,
          required_course_count: 1,
          nun_required_finished_course_count: 0,
          nun_required_course_count: 1,
        },
      });
    }

    if (pathname === "/api/v1/user/latest-learn") {
      return json(route, {
        resource_url: {},
        user_latest_learns: [
          {
            course: courses[1],
            record: { progress: 3500 },
            hour_record: { updated_at: new Date().toISOString() },
          },
        ],
      });
    }

    if (pathname === "/api/v1/user/password") {
      return json(route, true);
    }

    if (pathname === "/api/v1/user/avatar") {
      return json(route, true);
    }

    if (pathname === "/api/v1/course/101") {
      return json(route, {
        course: courses[0],
        chapters: [{ id: 501, course_id: 101, name: "第一章", sort: 1 }],
        hours: {
          501: [
            {
              id: 1001,
              course_id: 101,
              chapter_id: 501,
              duration: 60,
              rid: 9001,
              sort: 1,
              title: "安全意识基础",
              type: "video",
            },
            {
              id: 1002,
              course_id: 101,
              chapter_id: 501,
              duration: 80,
              rid: 9002,
              sort: 2,
              title: "密码使用规范",
              type: "video",
            },
          ],
        },
        learn_record: { progress: 5000, finished_count: 1 },
        learn_hour_records: hourRecords,
        attachments: [
          {
            id: 701,
            course_id: 101,
            rid: 8001,
            sort: 1,
            title: "安全学习手册",
            ext: "pdf",
            type: "file",
          },
        ],
      });
    }

    if (pathname === "/api/v1/course/102") {
      return json(route, {
        course: courses[1],
        chapters: [],
        hours: { 0: [] },
        learn_record: { progress: 3500, finished_count: 0 },
        learn_hour_records: {},
        attachments: [],
      });
    }

    if (pathname === "/api/v1/course/101/hour/1001") {
      return json(route, {
        course: courses[0],
        hour: {
          id: 1001,
          course_id: 101,
          chapter_id: 501,
          duration: 60,
          rid: 9001,
          title: "安全意识基础",
          type: "video",
        },
        user_hour_record: hourRecords[1001],
      });
    }

    if (pathname === "/api/v1/course/101/hour/1001/play") {
      return json(route, { resource_url: { 9001: "https://media.example.test/lesson.mp4" } });
    }

    if (
      pathname === "/api/v1/course/101/hour/1001/record" ||
      pathname === "/api/v1/course/101/hour/1001/ping"
    ) {
      return json(route, true);
    }

    if (pathname === "/api/v1/course/101/attach/701/download") {
      return json(route, { resource_url: { 8001: "about:blank?attachment=security-guide" } });
    }

    if (pathname === "/api/v1/question-bank/banks/list") {
      return json(route, [
        {
          id: 8,
          name: "信息安全题库",
          description: "安全意识客观题练习",
          questionCount: 3,
        },
      ]);
    }

    if (pathname === "/api/v1/question-bank/questions/list") {
      return json(route, {
        items: [
          {
            id: 21,
            bankId: 8,
            code: "SAFE-001",
            type: "single_choice",
            difficulty: "easy",
            stem: "收到可疑邮件时应该怎么做？",
            options: [
              { id: "a", text: "点击链接确认" },
              { id: "b", text: "报告安全团队" },
            ],
            suggestedScore: 2,
            tags: ["邮件安全"],
            version: 1,
          },
          {
            id: 22,
            bankId: 8,
            code: "SAFE-002",
            type: "multiple_choice",
            difficulty: "medium",
            stem: "以下哪些属于强密码实践？",
            options: [
              { id: "a", text: "使用足够长度" },
              { id: "b", text: "不同系统使用不同密码" },
              { id: "c", text: "与同事共享密码" },
            ],
            suggestedScore: 3,
            tags: ["密码安全"],
            version: 1,
          },
          {
            id: 23,
            bankId: 8,
            code: "SAFE-003",
            type: "true_false",
            difficulty: "easy",
            stem: "工作账号可以与他人共享。",
            options: [],
            suggestedScore: 2,
            tags: ["账号安全"],
            version: 1,
          },
        ],
        total: 3,
        page: 1,
        size: 100,
      });
    }

    if (pathname === "/api/v1/question-bank/practice/submit") {
      const answer = body as { questionId?: number };
      const results = {
        21: { standardAnswer: { optionIds: ["b"] }, analysis: "不要点击未知链接。", maxScore: 2 },
        22: { standardAnswer: { optionIds: ["a", "b"] }, analysis: "密码应足够长且避免复用。", maxScore: 3 },
        23: { standardAnswer: { value: false }, analysis: "工作账号仅限本人使用。", maxScore: 2 },
      } as const;
      const result = results[answer.questionId as keyof typeof results];
      return json(route, {
        id: 90 + Number(answer.questionId || 0),
        score: result.maxScore,
        maxScore: result.maxScore,
        result: "correct",
        standardAnswer: result.standardAnswer,
        analysis: result.analysis,
        version: 1,
      });
    }

    if (pathname === "/api/v1/question-bank/practice/history") {
      const pageNumber = Number((body as { page?: number })?.page || 1);
      return json(route, {
        items: [
          {
            id: pageNumber,
            questionId: 21,
            stem: pageNumber === 1 ? "收到可疑邮件时应该怎么做？" : "第二页作答记录",
            score: 2,
            maxScore: 2,
            result: "correct",
            createdAt: "2026-09-09T08:30:00",
          },
        ],
        total: 11,
        page: pageNumber,
        size: 10,
      });
    }

    return route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ code: 404, data: null, msg: `Unhandled mock: ${pathname}` }),
    });
  });
}

export const test = base.extend<H5Fixtures>({
  authenticated: [true, { option: true }],
  ldapEnabled: [false, { option: true }],
  apiCalls: async ({}, use) => {
    await use([]);
  },
  page: async ({ page, authenticated, apiCalls, ldapEnabled }, use) => {
    if (authenticated) {
      await page.addInitScript(() => {
        if (sessionStorage.getItem("playedu-h5-e2e-seeded") === "1") {
          return;
        }
        localStorage.setItem("playedu-h5-token", "h5-e2e-token");
        localStorage.setItem("playedu-h5-depatmentKey", "1");
        localStorage.setItem("playedu-h5-depatmentName", "研发中心");
        sessionStorage.setItem("playedu-h5-e2e-seeded", "1");
      });
    }
    await mockApi(page, apiCalls, ldapEnabled);
    await use(page);
  },
});

export { expect };
