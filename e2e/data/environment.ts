import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

export const urls = {
  admin: process.env.E2E_ADMIN_URL ?? "http://127.0.0.1:9900",
  pc: process.env.E2E_PC_URL ?? "http://127.0.0.1:9800",
  h5: process.env.E2E_H5_URL ?? "http://127.0.0.1:9801",
  api: process.env.E2E_API_URL ?? "http://127.0.0.1:9700",
};

export const authFiles = {
  admin: path.join(root, ".auth", "admin.json"),
  pc: path.join(root, ".auth", "pc-student.json"),
  h5: path.join(root, ".auth", "h5-student.json"),
};

export const tokenKeys = {
  admin: "playedu-backend-token",
  pc: "playedu-frontend-token",
  h5: "playedu-h5-token",
} as const;

export const fixtureIds = {
  courseId: positiveInteger("E2E_COURSE_ID"),
  courseTitle: process.env.E2E_COURSE_TITLE,
  questionBankId: positiveInteger("E2E_QUESTION_BANK_ID"),
  questionBankName: process.env.E2E_QUESTION_BANK_NAME,
};

export function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`Missing ${name}. Copy e2e/.env.example to e2e/.env and configure the acceptance account.`);
  }
  return value;
}

function positiveInteger(name: string): number | undefined {
  const raw = process.env[name]?.trim();
  if (!raw) return undefined;
  const value = Number(raw);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`${name} must be a positive integer.`);
  }
  return value;
}
