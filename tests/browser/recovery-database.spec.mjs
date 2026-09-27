import { test, expect } from "@playwright/test";
import net from "node:net";
import { randomUUID } from "node:crypto";

test("a reader registers through SMTP, signs in, resets the password and loses the old session", async ({ page, browser }) => {
  test.setTimeout(60_000);
  expect(process.env.BLOG_SMTP_HOST).toBe("127.0.0.1");
  expect(process.env.BLOG_SMTP_MODE).toBe("local");
  const emails = [];
  const commands = [];
  const sockets = new Set();
  const server = net.createServer(socket => {
    sockets.add(socket);
    commands.push("CONNECTED");
    socket.on("close", () => sockets.delete(socket));
    socket.write("220 localhost capture\r\n");
    let pending = "", data = false, message = "";
    socket.on("data", chunk => {
      pending += chunk.toString("utf8");
      let end;
      while ((end = pending.indexOf("\r\n")) >= 0) {
        const line = pending.slice(0, end);
        pending = pending.slice(end + 2);
        if (!data) commands.push(line.split(" ")[0]);
        if (data && line !== ".") message += line.replace(/^\.\./, ".") + "\r\n";
        else if (data) {
          emails.push(message.replace(/=\r\n/g, "").replace(/=3D/g, "="));
          data = false; message = "";
          socket.write("250 Captured\r\n");
        } else if (line === "DATA") {
          data = true; socket.write("354 Send data\r\n");
        } else if (line === "QUIT") socket.end("221 Bye\r\n");
        else socket.write("250 localhost\r\n");
      }
    });
  });
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(Number(process.env.BLOG_SMTP_PORT || 1025), "127.0.0.1", resolve);
  });
  const context = await browser.newContext();
  try {
    const email = "browser-recovery-" + randomUUID() + "@example.test";
    const password = "a long browser registration passphrase";
    const replacement = "a different browser reset passphrase";
    await page.goto("/en/register");
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByRole("button", { name: "Send email" }).click();
    await expect(page.getByRole("status")).toContainText("If this address is eligible");
    await expect.poll(() => ({ delivered: emails.length, commands }), { timeout: 15_000 }).toMatchObject({ delivered: 1 });
    const confirm = emails[0].match(/http:\/\/127\.0\.0\.1:18080\/en\/confirm#token=[A-Za-z0-9_-]{43}/)[0];
    expect(emails[0]).toContain(email);
    await page.goto(confirm);
    await page.getByLabel("New password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Save password" }).click();
    await expect(page.getByRole("status")).toContainText("Your account is ready");
    await page.getByRole("link", { name: "Back to sign in" }).click();
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.getByText("Reader", { exact: true })).toBeVisible();

    const recovery = await context.newPage();
    await recovery.goto("http://127.0.0.1:18080/en/forgot-password");
    await recovery.getByLabel("Email", { exact: true }).fill(email);
    await recovery.getByRole("button", { name: "Send email" }).click();
    await expect.poll(() => ({ delivered: emails.length, commands }), { timeout: 15_000 }).toMatchObject({ delivered: 2 });
    const reset = emails[1].match(/http:\/\/127\.0\.0\.1:18080\/en\/reset-password#token=[A-Za-z0-9_-]{43}/)[0];
    await recovery.goto(reset);
    await recovery.getByLabel("New password", { exact: true }).fill(replacement);
    await recovery.getByRole("button", { name: "Save password" }).click();
    await expect(recovery.getByRole("status")).toContainText("Your password has been changed");
    await page.reload();
    await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
    await page.getByLabel("Email", { exact: true }).fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.getByRole("alert")).toHaveText("Invalid email or password.");
    await page.getByLabel("Password", { exact: true }).fill(replacement);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.getByText("Signed in as", { exact: true })).toBeVisible();
    await recovery.goto(reset);
    await recovery.getByLabel("New password", { exact: true }).fill(password);
    await recovery.getByRole("button", { name: "Save password" }).click();
    await expect(recovery.getByRole("alert")).toContainText("invalid or has expired");
    // This one reader intentionally remains in the dedicated browser test database.
  } finally {
    await context.close();
    sockets.forEach(socket => socket.destroy());
    await new Promise(resolve => server.close(resolve));
  }
});
