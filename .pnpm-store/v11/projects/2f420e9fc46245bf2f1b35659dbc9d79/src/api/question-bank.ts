import client from "./internal/httpClient";
import { message } from "antd";

export function showRequestError(error: unknown) {
  // Business errors with msg are already displayed by the shared HTTP client.
  if (!error || typeof error !== "object" || !("msg" in error)) {
    message.error("请求失败，请检查网络连接后重试");
  }
}

export const questionTypes = { single_choice: "单选题", multiple_choice: "多选题", true_false: "判断题", short_answer: "简答题" };
export const difficulties = { easy: "简单", medium: "中等", hard: "困难" };
export const statuses = { draft: "草稿", enabled: "启用", disabled: "停用", archived: "归档" };
export interface Bank {
  id: number; revision: number; name: string; description: string;
  status: "enabled" | "disabled"; practiceEnabled: boolean; questionCount: number;
}
export interface Category { id: number; bankId: number; parentId: number; name: string }
export interface QuestionInput {
  id?: number; expectedVersion?: number; bankId: number; categoryId?: number | null;
  code: string; type: keyof typeof questionTypes; difficulty: keyof typeof difficulties;
  stem: string; options: { id: string; text: string }[];
  standardAnswer: { optionIds?: string[]; value?: boolean; text?: string };
  gradingRule: { strategy: "exact_match" | "manual" }; suggestedScore: number;
  analysis: string; tags: string[]; status: keyof typeof statuses;
}
export interface Question extends QuestionInput { id: number; version: number }
export interface Query {
  bankId?: number; categoryId?: number; keyword?: string; type?: string;
  difficulty?: string; status?: string; tag?: string; page: number; size: number;
}
export interface Version { version: number; createdBy: number; createdAt: string }
async function post<T>(path: string, body: object = {}): Promise<T> {
  const result = await client.post(`/backend/v1/question-bank/${path}`, body) as { data: T };
  return result.data;
}
export const banks = () => post<Bank[]>("banks/list");
export const saveBank = (body: Partial<Bank>) => post<Bank>("banks/save", body);
export const deleteBank = (id: number) => post("banks/delete", { id });
export const categories = (bankId: number) => post<Category[]>("categories/list", { bankId });
export const createCategory = (body: Omit<Category, "id">) => post("categories/create", body);
export const renameCategory = (id: number, name: string) => post("categories/rename", { id, name });
export const deleteCategory = (id: number) => post("categories/delete", { id });
export const questions = (body: Query) => post<{ items: Question[]; total: number }>("questions/list", body);
export const detail = (id: number, version?: number) => post<Question>("questions/detail", { id, version });
export const saveQuestion = (body: QuestionInput) => post<Question>("questions/save", body);
export const deleteDraft = (id: number) => post("questions/delete-draft", { id });
export const changeStatus = (q: Question, status: QuestionInput["status"]) => post("questions/status", { id: q.id, expectedVersion: q.version, status });
export const versions = (id: number) => post<Version[]>("questions/versions", { id });
export const importQuestions = (bankId: number, questions: QuestionInput[]) => post<{ count: number }>("questions/import", { bankId, questions });
export const exportQuestions = (bankId: number) => post<Question[]>("questions/export", { bankId });
