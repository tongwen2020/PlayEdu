// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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
    submit: vi.fn(),
  },
}));

const mockedExam = vi.mocked(exam);

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
  });

  it("展示开放题库，并可切换查看我的作答记录", async () => {
    mockedExam.banks.mockResolvedValue([
      { id: 8, name: "信息安全基础", description: "安全意识必修练习", questionCount: 12 },
    ]);
    mockedExam.history.mockResolvedValue({
      items: [
        {
          id: 3,
          questionId: 11,
          stem: "收到可疑邮件时应如何处理？",
          score: 2,
          maxScore: 2,
          result: "correct",
          createdAt: "2026-09-08T09:30:00",
        },
      ],
      total: 1,
      page: 1,
      size: 10,
    });

    renderPage(<ExamCenterPage />);

    expect(await screen.findByText("信息安全基础")).toBeInTheDocument();
    expect(screen.getByText("12 道题")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("tab", { name: "我的作答记录" }));
    expect(await screen.findByText("收到可疑邮件时应如何处理？")).toBeInTheDocument();
    expect(screen.getByText("2", { selector: "strong" })).toBeInTheDocument();
  });
});

describe("在线答题页面", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("选择判断题答案后提交，并展示判分和答案解析", async () => {
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
      size: 100,
    });
    mockedExam.submit.mockResolvedValue({
      id: 99,
      score: 2,
      maxScore: 2,
      result: "correct",
      standardAnswer: { value: false },
      analysis: "账号仅限本人使用，不得共享。",
      version: 1,
    });

    renderPage(
      <Routes>
        <Route path="/exam/practice/:bankId" element={<ExamPracticePage />} />
      </Routes>,
      "/exam/practice/8"
    );

    expect(await screen.findByText("工作账号可以与他人共享。")).toBeInTheDocument();
    const submitButton = screen.getByRole("button", { name: "提交答案" });
    expect(submitButton).toBeDisabled();

    await userEvent.click(screen.getByText("错误"));
    expect(submitButton).toBeEnabled();
    await userEvent.click(submitButton);

    expect(await screen.findByText("回答正确")).toBeInTheDocument();
    expect(screen.getByText(/账号仅限本人使用，不得共享/)).toBeInTheDocument();
    await waitFor(() =>
      expect(mockedExam.submit).toHaveBeenCalledWith(
        21,
        1,
        { value: false },
        expect.stringMatching(/^[a-f0-9-]{36}$/)
      )
    );
  });
});
