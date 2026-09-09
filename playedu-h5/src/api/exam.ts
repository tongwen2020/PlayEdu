import client from "./internal/httpClient";

export const questionTypes = {
  single_choice: "单选题",
  multiple_choice: "多选题",
  true_false: "判断题",
} as const;

export const difficulties = {
  easy: "简单",
  medium: "中等",
  hard: "困难",
} as const;

export interface PracticeBank {
  id: number;
  name: string;
  description: string;
  questionCount: number;
}

export interface QuestionOption {
  id: string;
  text: string;
}

export interface PracticeAnswer {
  optionIds?: string[];
  value?: boolean;
  text?: string;
}

export interface PracticeQuestion {
  id: number;
  bankId: number;
  categoryId?: number | null;
  code: string;
  type: keyof typeof questionTypes;
  difficulty: keyof typeof difficulties;
  stem: string;
  options: QuestionOption[];
  suggestedScore: number;
  tags: string[];
  version: number;
}

export interface PracticeResult {
  id: number;
  score: number;
  maxScore: number;
  result: "correct" | "incorrect";
  standardAnswer: PracticeAnswer;
  analysis: string;
  version: number;
}

export interface PracticeHistoryItem {
  id: number;
  questionId: number;
  stem: string;
  score: number;
  maxScore: number;
  result: "correct" | "incorrect";
  createdAt: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

async function post<T>(path: string, body: object = {}): Promise<T> {
  const result = (await client.post(`/api/v1/question-bank/${path}`, body)) as {
    data: T;
  };
  return result.data;
}

export const banks = () => post<PracticeBank[]>("banks/list");

export const questions = (bankId: number, page = 1, size = 100) =>
  post<PageResult<PracticeQuestion>>("questions/list", { bankId, page, size });

export const submit = (
  questionId: number,
  version: number,
  answer: PracticeAnswer,
  requestKey: string
) =>
  post<PracticeResult>("practice/submit", {
    questionId,
    version,
    answer,
    requestKey,
  });

export const history = (page = 1, size = 10) =>
  post<PageResult<PracticeHistoryItem>>("practice/history", { page, size });
