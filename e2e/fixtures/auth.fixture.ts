import { expect, type Browser } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";
import { LoginPage } from "../pages/login.page";

export async function saveAuthenticatedState(options: {
  browser: Browser;
  kind: "admin" | "pc" | "h5";
  url: string;
  account: string;
  password: string;
  tokenKey: string;
  outputFile: string;
}): Promise<void> {
  await fs.mkdir(path.dirname(options.outputFile), { recursive: true });
  const context = await options.browser.newContext();
  const page = await context.newPage();
  try {
    const login = new LoginPage(page, options.kind);
    await login.goto(options.url);
    await login.login(options.account, options.password);
    await expect.poll(() => page.evaluate(key => localStorage.getItem(key), options.tokenKey)).toBeTruthy();
    await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
    await context.storageState({ path: options.outputFile });
  } finally {
    await context.close();
  }
}
