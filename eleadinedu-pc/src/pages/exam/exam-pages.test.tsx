// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ConfigProvider } from "antd";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import ExamCenterPage from "./index";
import ExamPracticePage from "./practice";
import { exam } from "../../api";
import "../../test/setup";

vi.mock("../../api", () => ({
  exam: {
    questionTypes: {
      single_choice: "单选题",
      multiple_choice: "多选题",
      true_false: "判断题",
    },
    difficulties: { easy: "简单", medium: "中等", hard: "困难" },
    banks: vi.fn(),
    questions: vi.fn(),
    history: vi.fn(),
    records: vi.fn(),
    recordDetail: vi.fn(),
    submit: vi.fn(),
    submitPaper: vi.fn(),
    papers: vi.fn(),
    paperDetail: vi.fn(),
    submitFixedPaper: vi.fn(),
  },
}));

const mockedExam = vi.mocked(exam);

afterEach(cleanup);

function renderPage(node: React.ReactNode, path = "/exam") {
  return render(
    <ConfigProvider>
      <MemoryRouter initialEntries={[path]}>{node}</MemoryRouter>
    </ConfigProvider>
  );
}

describe("考试中心页面", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedExam.papers.mockResolvedValue({ items: [], total: 0, page: 1, size: 100 });
  });

  it("展示开放题库，并可切换查看考试记录", async () => {
    mockedExam.banks.mockResolvedValue([
      { id: 8, name: "信息安全基础", description: "安全意识必修练习", questionCount: 12 },
    ]);
    mockedExam.papers.mockResolvedValue({
      items: [{ id: 6, code: "P-6", name: "安全正式考试", description: "", version: 1, questionCount: 10, totalScore: 100, passScore: 90, requiresManualGrading: false }],
      total: 1,
      page: 1,
      size: 100,
    });
    mockedExam.records.mockResolvedValue({
      items: [
        {
          id: 3,
          paperId: 6,
          paperCode: "P-6",
          paperName: "安全正式考试",
          version: 1,
          fixedPaperAttemptId: 100,
          examTime: "2026-09-08T09:30:00",
          score: 92,
          maxScore: 100,
          passScore: 90,
          passed: true,
          questionCount: 10,
          correctCount: 9,
        },
      ],
      total: 1,
      page: 1,
      size: 10,
    });
    mockedExam.recordDetail.mockResolvedValue({
      id: 3,
      paperId: 6,
      paperCode: "P-6",
      paperName: "安全正式考试",
      version: 1,
      fixedPaperAttemptId: 100,
      examTime: "2026-09-08T09:30:00",
      score: 92,
      maxScore: 100,
      passScore: 90,
      passed: true,
      questionCount: 1,
      correctCount: 1,
      sections: [{
        title: "判断题",
        description: "",
        position: 0,
        items: [{
          questionId: 21,
          questionVersion: 1,
          position: 0,
          question: {
            id: 21,
            bankId: 8,
            code: "SAFE-001",
            type: "true_false",
            difficulty: "easy",
            stem: "工作账号可以与他人共享。",
            options: [],
            suggestedScore: 100,
            tags: [],
            version: 1,
          },
          submittedAnswer: { value: false },
          standardAnswer: { value: false },
          score: 100,
          maxScore: 100,
          result: "correct",
          analysis: "账号仅限本人使用。",
        }],
      }],
    });

    renderPage(<ExamCenterPage />);

    expect(await screen.findByText("安全正式考试")).toBeInTheDocument();
    expect(screen.getByText("及格 90 分")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("tab", { name: "开放题库" }));
    expect(await screen.findByText("信息安全基础")).toBeInTheDocument();
    expect(screen.getByText("12 道题")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "考试记录" }));
    expect((await screen.findAllByText("安全正式考试")).length).toBeGreaterThan(0);
    expect(screen.getByText("92", { selector: "strong" })).toBeInTheDocument();
    expect(screen.getByText("已通过")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "查看答题快照" }));
    expect(await screen.findByText(/工作账号可以与他人共享。/)).toBeInTheDocument();
    expect(screen.getAllByText("错误").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("账号仅限本人使用。")).toBeInTheDocument();
  });
});

describe("在线答题页面", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("提交整卷后自动阅卷，并展示总分和答案解析", async () => {
    mockedExam.questions.mockResolvedValue({
      items: [
        {
          id: 21,
          bankId: 8,
          code: "SAFE-001",
          type: "true_false",
          difficulty: "easy",
          stem: "工作账号可以与他人共享。",
          options: [],
          suggestedScore: 2,
          tags: ["账号安全"],
          version: 1,
        },
      ],
      total: 1,
      page: 1,
      size: 500,
    });
    mockedExam.submitPaper.mockResolvedValue({
      id: 99,
      bankId: 8,
      score: 2,
      maxScore: 2,
      questionCount: 1,
      correctCount: 1,
      submittedAt: "2026-09-10T09:00:00",
      items: [{
        id: 99,
        questionId: 21,
        score: 2,
        maxScore: 2,
        result: "correct",
        standardAnswer: { value: false },
        analysis: "账号仅限本人使用，不得共享。",
        version: 1,
      }],
    });

    renderPage(
      <Routes>
        <Route path="/exam/practice/:bankId" element={<ExamPracticePage />} />
      </Routes>,
      "/exam/practice/8"
    );

    expect(await screen.findByText("工作账号可以与他人共享。")).toBeInTheDocument();
    await userEvent.click(screen.getByText("错误"));
    await userEvent.click(screen.getByRole("button", { name: "提交试卷" }));
    await userEvent.click(screen.getByRole("button", { name: "确认交卷" }));

    expect(await screen.findByText("回答正确")).toBeInTheDocument();
    expect(screen.getByText(/账号仅限本人使用，不得共享/)).toBeInTheDocument();
    await waitFor(() =>
      expect(mockedExam.submitPaper).toHaveBeenCalledWith(
        8,
        [{ questionId: 21, version: 1, answer: { value: false } }],
        expect.stringMatching(/^[a-f0-9-]{36}$/)
      )
    );
  }, 15000);

  it("正式试卷交卷后展示及格线和通过结果", async () => {
    const question = {
      id: 31, bankId: 8, code: "P-001", type: "true_false" as const,
      difficulty: "easy" as const, stem: "必须遵守安全规程。", options: [],
      suggestedScore: 10, tags: [], version: 1,
    };
    mockedExam.paperDetail.mockResolvedValue({
      id: 6, code: "P-6", name: "安全正式考试", description: "", version: 1,
      questionCount: 1, totalScore: 10, passScore: 9, requiresManualGrading: false,
      sections: [{ title: "判断题", description: "", position: 0, shuffleQuestions: false,
        items: [{ questionId: 31, questionVersion: 1, score: 10, position: 0, question }] }],
    });
    mockedExam.submitFixedPaper.mockResolvedValue({
      id: 100, paperId: 6, version: 1, score: 10, maxScore: 10,
      passScore: 9, passed: true, questionCount: 1, correctCount: 1,
      submittedAt: "2026-09-11T09:00:00",
      items: [{ id: 100, questionId: 31, score: 10, maxScore: 10, result: "correct",
        standardAnswer: { value: true }, analysis: "应始终遵守安全规程。", version: 1 }],
    });
    renderPage(
      <Routes><Route path="/exam/paper/:paperId" element={<ExamPracticePage />} /></Routes>,
      "/exam/paper/6"
    );
    expect(await screen.findByText("必须遵守安全规程。")).toBeInTheDocument();
    await userEvent.click(screen.getByText("正确"));
    await userEvent.click(screen.getByRole("button", { name: "提交试卷" }));
    await userEvent.click(screen.getByRole("button", { name: "确认交卷" }));
    expect(await screen.findByText(/已通过：10 \/ 10 分（及格线 9 分）/)).toBeInTheDocument();
  }, 15000);
});
