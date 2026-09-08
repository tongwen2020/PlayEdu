import { test as setup } from "@playwright/test";
import { authFiles, requiredEnv, tokenKeys, urls } from "../../data/environment";
import { saveAuthenticatedState } from "../../fixtures/auth.fixture";

setup("@smoke authenticate admin", async ({ browser }) => {
  await saveAuthenticatedState({
    browser,
    kind: "admin",
    url: urls.admin,
    account: requiredEnv("E2E_ADMIN_EMAIL"),
    password: requiredEnv("E2E_ADMIN_PASSWORD"),
    tokenKey: tokenKeys.admin,
    outputFile: authFiles.admin,
  });
});
