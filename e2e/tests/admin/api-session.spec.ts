import { test, expect } from "../../fixtures/ui.fixture";
import { authenticatedApi, expectSuccess } from "../../fixtures/api.fixture";

test("@smoke 后台登录态可以访问管理员详情 API", async () => {
  const api = await authenticatedApi("admin");
  try {
    const data = await expectSuccess(await api.get("/backend/v1/auth/detail"));
    expect(data).toBeTruthy();
  } finally {
    await api.dispose();
  }
});
