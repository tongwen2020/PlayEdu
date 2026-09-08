import { test as setup } from "@playwright/test";
import { authFiles, requiredEnv, tokenKeys, urls } from "../../data/environment";
import { saveAuthenticatedState } from "../../fixtures/auth.fixture";

setup("@smoke authenticate H5 student", async ({ browser }) => {
  await saveAuthenticatedState({
    browser,
    kind: "h5",
    url: urls.h5,
    account: requiredEnv("E2E_STUDENT_ACCOUNT"),
    password: requiredEnv("E2E_STUDENT_PASSWORD"),
    tokenKey: tokenKeys.h5,
    outputFile: authFiles.h5,
  });
});
