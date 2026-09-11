import client from "./internal/httpClient";
import { Query as QuestionQuery, Question } from "./question-bank";

export const paperStatuses = { draft: "草稿", published: "已发布", disabled: "已停用", archived: "已归档" } as const;
export type PaperStatus = keyof typeof paperStatuses;

export interface PaperCategory { id: number; ownerId: number; parentId: number; name: string }
export interface PaperItem { questionId: number; questionVersion: number; score: number; position: number; question?: Question }
export interface PaperSection { title: string; description: string; position: number; shuffleQuestions: boolean; items: PaperItem[] }
export interface PaperInput {
  id?: number; revision?: number; code: string; name: string; description: string;
  categoryId?: number | null; tags: string[]; sections: PaperSection[];
}
export interface Paper extends PaperInput {
  id: number; ownerId: number; mode: "fixed"; status: PaperStatus; revision: number;
  currentVersion: number; version?: number; createdAt: string; updatedAt: string;
  questionCount: number; totalScore: number; objectiveScore: number; subjectiveScore: number;
  requiresManualGrading: boolean;
}
export interface PaperQuery { categoryId?: number; keyword?: string; status?: string; tag?: string; page: number; size: number }
export interface PaperVersion {
  version: number; questionCount: number; totalScore: number; objectiveScore: number;
  subjectiveScore: number; requiresManualGrading: boolean; createdBy: number; createdAt: string;
}
export interface ValidationResult {
  valid: boolean; errors: string[]; warnings: string[];
  summary: Pick<Paper, "questionCount" | "totalScore" | "objectiveScore" | "subjectiveScore" | "requiresManualGrading">;
}

async function post<T>(path: string, body: object = {}): Promise<T> {
  const result = await client.post(`/backend/v1/exam-paper/${path}`, body) as { data: T };
  return result.data;
}

export const categories = () => post<PaperCategory[]>("categories/list");
export const createCategory = (parentId: number, name: string) => post<PaperCategory>("categories/create", { parentId, name });
export const renameCategory = (id: number, name: string) => post("categories/rename", { id, name });
export const deleteCategory = (id: number) => post("categories/delete", { id });
export const papers = (body: PaperQuery) => post<{ items: Paper[]; total: number; page: number; size: number }>("papers/list", body);
export const detail = (id: number, version?: number) => post<Paper>("papers/detail", { id, version });
export const savePaper = (body: PaperInput) => post<Paper>("papers/save", body);
export const deleteDraft = (id: number) => post("papers/delete-draft", { id });
export const validatePaper = (id: number) => post<ValidationResult>("papers/validate", { id });
export const publish = (id: number, expectedRevision: number) => post<Paper>("papers/publish", { id, expectedRevision });
export const changeStatus = (id: number, expectedRevision: number, status: Exclude<PaperStatus, "draft">) => post<Paper>("papers/status", { id, expectedRevision, status });
export const copy = (sourceId: number, version: number | undefined, code: string, name: string) => post<Paper>("papers/copy", { sourceId, version, code, name });
export const versions = (id: number) => post<PaperVersion[]>("papers/versions", { id });
export const exportPaper = (id: number, version?: number) => post<Paper>("papers/export", { id, version });
export const importPapers = (values: PaperInput[]) => post<{ count: number; ids: number[] }>("papers/import", { papers: values });
export const searchQuestions = (body: QuestionQuery) => post<{ items: Question[]; total: number; page: number; size: number }>("questions/search", body);
