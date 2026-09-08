import { test as setup } from "@playwright/test";
import { authFiles, requiredEnv, tokenKeys, urls } from "../../data/environment";
import { saveAuthenticatedState } from "../../fixtures/auth.fixture";

setup("@smoke authenticate PC student", async ({ browser }) => {
  await saveAuthenticatedState({
    browser,
    kind: "pc",
    url: urls.pc,
    account: requiredEnv("E2E_STUDENT_ACCOUNT"),
    password: requiredEnv("E2E_STUDENT_PASSWORD"),
    tokenKey: tokenKeys.pc,
    outputFile: authFiles.pc,
  });
});
