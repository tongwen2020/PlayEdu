import { request, type APIRequestContext, type APIResponse, expect } from "@playwright/test";
import fs from "node:fs/promises";
import { authFiles, tokenKeys, urls } from "../data/environment";

type Role = keyof typeof authFiles;

export async function authenticatedApi(role: Role): Promise<APIRequestContext> {
  const state = JSON.parse(await fs.readFile(authFiles[role], "utf8")) as {
    origins?: Array<{ localStorage?: Array<{ name: string; value: string }> }>;
  };
  const token = state.origins
    ?.flatMap(origin => origin.localStorage ?? [])
    .find(item => item.name === tokenKeys[role])?.value;
  if (!token) throw new Error(`No ${role} token found in ${authFiles[role]}. Run the matching auth setup project first.`);

  return request.newContext({
    baseURL: urls.api,
    extraHTTPHeaders: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
    },
  });
}

export async function expectSuccess(response: APIResponse): Promise<unknown> {
  expect(response.ok(), await response.text()).toBeTruthy();
  const body = await response.json() as { code?: number; data?: unknown; msg?: string };
  expect(body.code, body.msg).toBe(0);
  return body.data;
}
