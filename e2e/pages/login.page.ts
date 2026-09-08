import { expect, type Page } from "@playwright/test";

export class LoginPage {
  constructor(
    private readonly page: Page,
    private readonly kind: "admin" | "pc" | "h5",
  ) {}

  async goto(url: string): Promise<void> {
    await this.page.goto(`${url}/login`);
    await expect(this.page.getByText(this.kind === "admin" ? "后台登录" : "学员登录")).toBeVisible();
  }

  async login(account: string, password: string): Promise<void> {
    const accountPlaceholder = this.kind === "admin" ? "请输入管理员邮箱账号" : "请输入邮箱或UID";
    await this.page.getByPlaceholder(accountPlaceholder).fill(account);
    await this.page.getByPlaceholder("请输入密码").fill(password);
    await this.page.getByRole("button", { name: this.kind === "h5" ? /登\s*录/ : "立即登录" }).click();
  }
}
