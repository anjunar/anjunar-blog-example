import { test, expect } from "@playwright/test";

const account = { id: "account-id", version: 0, email: "admin@example.com", role: "ADMIN" };
const reply = (route, body, status = 200) => route.fulfill({
  status, contentType: "application/json", body: JSON.stringify(body),
});

async function mockAccount(page) {
  let signedIn = false;
  let token = "anonymous-token";
  await page.route("**/service/auth/**", async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith("/session")) {
      return reply(route, { csrfToken: token, ...(signedIn ? { account } : {}) });
    }
    expect(request.headers()["x-csrf-token"]).toBe(token);
    if (path.endsWith("/login")) {
      const body = request.postDataJSON();
      expect(Object.keys(body).sort()).toEqual(["email", "password"]);
      if (body.password !== "a long tutorial passphrase") return reply(route, {}, 401);
      signedIn = true;
      token = "signed-in-token";
      return reply(route, { csrfToken: token, account });
    }
    if (path.endsWith("/logout")) {
      signedIn = false;
      token = "signed-out-token";
      return reply(route, { csrfToken: token });
    }
    return reply(route, {}, 404);
  });
}

async function fill(page, password = "a long tutorial passphrase") {
  await page.getByLabel("Email", { exact: true }).fill(account.email);
  await page.getByLabel("Password", { exact: true }).fill(password);
}

test("credential bindings, Enter, reload and logout follow the session state", async ({ page }, testInfo) => {
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await mockAccount(page);
  await page.goto("/en/account");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Your account");
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute("autocomplete", "current-password");
  await page.screenshot({ path: testInfo.outputPath("sign-in-desktop.png"), fullPage: true });
  await fill(page);
  await page.getByLabel("Password", { exact: true }).press("Enter");
  await expect(page.getByText("Signed in as", { exact: true })).toBeVisible();
  await expect(page.getByText(account.email, { exact: true })).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveCount(0);
  await page.reload();
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
  expect(errors).toEqual([]);
});

test("failed credentials clear the password and allow another attempt", async ({ page }) => {
  await mockAccount(page);
  await page.goto("/en/account");
  await fill(page, "incorrect password");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("Invalid email or password.");
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
  await expect(page.getByLabel("Email", { exact: true })).toHaveValue(account.email);
  await fill(page);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByText("Signed in as", { exact: true })).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("rate limits have a clear message and the form fits a mobile screen", async ({ page }, testInfo) => {
  await mockAccount(page);
  await page.route("**/service/auth/login", route => reply(route, {}, 429));
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/en/account");
  await fill(page);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Please wait a minute");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("sign-in-mobile.png"), fullPage: true });
});

test("pending sign-in disables duplicate submissions and does not redraw a departed page", async ({ page }) => {
  await mockAccount(page);
  await page.route("**/service/blog/posts?**", route => reply(route, { size: 0 }));
  let release;
  const pending = new Promise(resolve => release = resolve);
  let completed;
  const settled = new Promise(resolve => completed = resolve);
  let attempts = 0;
  await page.route("**/service/auth/login", async route => {
    attempts++;
    await pending;
    await reply(route, { csrfToken: "new-token", account });
    completed();
  });
  await page.goto("/en/account");
  await fill(page);
  const started = page.waitForRequest("**/service/auth/login");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await started;
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
  await expect(page.getByRole("status")).toHaveText("Please wait…");
  await page.getByRole("navigation", { name: "Main navigation" }).getByRole("link", { name: "Latest posts" }).click();
  await expect(page.getByText("No posts have been published yet.")).toBeVisible();
  release();
  await settled;
  expect(attempts).toBe(1);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("From idea to working software.");
});

test("session lookup failures offer a document retry", async ({ page }) => {
  let attempts = 0;
  await page.route("**/service/auth/session", route =>
    ++attempts === 1 ? reply(route, {}, 503) : reply(route, { csrfToken: "token" }));
  await page.goto("/en/account");
  await expect(page.getByRole("alert")).toHaveText("Sign-in is unavailable. Please try again.");
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
});
