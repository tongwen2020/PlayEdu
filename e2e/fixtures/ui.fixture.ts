import { test as base, expect } from "@playwright/test";

type Diagnostics = {
  browserDiagnostics: string[];
};

export const test = base.extend<Diagnostics>({
  browserDiagnostics: [
    async ({ page }, use, testInfo) => {
      const messages: string[] = [];
      page.on("pageerror", error => messages.push(`pageerror: ${error.message}`));
      page.on("console", message => {
        if (message.type() === "error") messages.push(`console: ${message.text()}`);
      });
      page.on("response", response => {
        if (response.status() >= 500) messages.push(`http ${response.status()}: ${response.url()}`);
      });

      await use(messages);

      if (messages.length) {
        await testInfo.attach("browser-diagnostics", {
          body: Buffer.from(messages.join("\n"), "utf8"),
          contentType: "text/plain",
        });
      }
    },
    { auto: true },
  ],
});

export { expect };
