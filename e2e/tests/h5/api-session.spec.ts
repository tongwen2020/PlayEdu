import { test, expect } from "../../fixtures/ui.fixture";
import { authenticatedApi, expectSuccess } from "../../fixtures/api.fixture";

test("@smoke H5 登录态可以访问学员详情 API", async () => {
  const api = await authenticatedApi("h5");
  try {
    const data = await expectSuccess(await api.get("/api/v1/user/detail"));
    expect(data).toBeTruthy();
  } finally {
    await api.dispose();
  }
});
