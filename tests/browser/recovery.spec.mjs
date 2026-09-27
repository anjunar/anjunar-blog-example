import { test, expect } from "@playwright/test";

const reply = (route, body, status = 200) => route.fulfill({
  status, contentType: "application/json", body: JSON.stringify(body),
});
const token = "A".repeat(43);
async function session(page) {
  await page.route("**/service/auth/session", route => reply(route, { csrfToken: "csrf" }));
}

test("registration asks only for email, uses CSRF and offers a resend", async ({ page }, testInfo) => {
  await session(page);
  let calls = 0;
  await page.route("**/service/auth/register", route => {
    calls++;
    expect(route.request().headers()["x-csrf-token"]).toBe("csrf");
    expect(route.request().postDataJSON()).toEqual({ email: "reader@example.test" });
    return reply(route, { outcome: "accepted" }, 202);
  });
  await page.goto("/en/register");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Create an account");
  await expect(page.getByLabel("New password", { exact: true })).toHaveCount(0);
  await page.getByLabel("Email", { exact: true }).fill("reader@example.test");
  await page.screenshot({ path: testInfo.outputPath("registration-desktop.png"), fullPage: true });
  await page.getByRole("button", { name: "Send email" }).click();
  await expect(page.getByRole("status")).toContainText("If this address is eligible");
  await page.getByRole("button", { name: "Request a new link" }).click();
  await expect(page.getByRole("button", { name: "Send email" })).toBeVisible();
  await page.getByRole("button", { name: "Send email" }).click();
  await expect(page.getByRole("status")).toContainText("If this address is eligible");
  expect(calls).toBe(2);
});

test("confirmation reads a fragment without submitting on GET and clears the password", async ({ page }) => {
  await session(page);
  let attempts = 0;
  const seen = [];
  page.on("request", request => seen.push(request.url()));
  await page.route("**/service/auth/confirm", route => {
    attempts++;
    expect(route.request().postDataJSON()).toEqual({ token, password: "a long confirmed passphrase" });
    return reply(route, { outcome: "completed" });
  });
  await page.goto("/en/confirm#token=" + token);
  await expect(page).toHaveURL(/\/en\/confirm$/);
  await expect(page.getByLabel("New password", { exact: true })).toHaveAttribute("autocomplete", "new-password");
  expect(attempts).toBe(0);
  expect(seen.every(url => !url.includes(token))).toBe(true);
  await page.getByLabel("New password", { exact: true }).fill("a long confirmed passphrase");
  await page.getByLabel("New password", { exact: true }).press("Enter");
  await expect(page.getByRole("status")).toHaveText("Your account is ready. You can now sign in.");
  await expect(page.getByLabel("New password", { exact: true })).toHaveCount(0);
  expect(attempts).toBe(1);
});

test("missing links and expired links give recovery paths on mobile", async ({ page }, testInfo) => {
  await session(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/en/reset-password");
  await expect(page.getByRole("alert")).toContainText("Open the link from your email");
  await expect(page.getByRole("button", { name: "Save password" })).toHaveCount(0);
  await page.route("**/service/auth/reset-password", route => reply(route, {}, 400));
  await page.goto("/en/reset-password#token=" + token);
  await page.getByLabel("New password", { exact: true }).fill("a long reset passphrase");
  await page.getByRole("button", { name: "Save password" }).click();
  await expect(page.getByRole("alert")).toContainText("invalid or has expired");
  await expect(page.getByLabel("New password", { exact: true })).toHaveValue("");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("recovery-mobile.png"), fullPage: true });
  await page.getByRole("link", { name: "Request a new link" }).click();
  await expect(page).toHaveURL(/\/en\/forgot-password$/);
});

test("mail throttling and pending recovery do not allow duplicate submissions", async ({ page }) => {
  await session(page);
  let release;
  const pending = new Promise(resolve => release = resolve);
  let calls = 0;
  await page.route("**/service/auth/forgot-password", async route => {
    calls++;
    await pending;
    await reply(route, {}, 429);
  });
  await page.goto("/en/forgot-password");
  await page.getByLabel("Email", { exact: true }).fill("reader@example.test");
  const started = page.waitForRequest("**/service/auth/forgot-password");
  await page.getByRole("button", { name: "Send email" }).click();
  await started;
  await expect(page.getByRole("button", { name: "Send email" })).toBeDisabled();
  release();
  await expect(page.getByRole("alert")).toContainText("Please wait a minute");
  expect(calls).toBe(1);
});
