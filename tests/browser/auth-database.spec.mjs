import { test, expect } from "@playwright/test";

test("the bootstrapped administrator signs in, survives reload, and signs out", async ({ page, context }, testInfo) => {
  const email = process.env.BLOG_TEST_ADMIN_EMAIL;
  const password = process.env.BLOG_TEST_ADMIN_PASSWORD;
  expect(email, "Set BLOG_TEST_ADMIN_EMAIL for a dedicated test administrator").toBeTruthy();
  expect(password, "Set BLOG_TEST_ADMIN_PASSWORD for that account").toBeTruthy();
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  await page.goto("/en/account");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByText("Signed in as", { exact: true })).toBeVisible();
  await expect(page.getByText(email, { exact: true })).toBeVisible();
  await expect(page.getByText("Administrator", { exact: true })).toBeVisible();
  const cookie = (await context.cookies()).find(cookie => cookie.name === "BLOGSESSION");
  expect(cookie.httpOnly).toBe(true);
  expect(cookie.sameSite).toBe("Lax");
  expect(await page.evaluate(() => document.cookie)).not.toContain("BLOGSESSION");
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  const me = await context.request.get("/service/auth/me");
  expect(me.status()).toBe(200);
  expect((await me.json()).data.email).toBe(email);
  // A real same-origin request without its synchronizer token cannot sign the user out.
  expect((await context.request.post("/service/auth/logout", { data: {} })).status()).toBe(403);
  await page.reload();
  await expect(page.getByText(email, { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("signed-in.png"), fullPage: true });
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
  expect((await context.request.get("/service/auth/me")).status()).toBe(401);
  expect(errors).toEqual([]);
});
