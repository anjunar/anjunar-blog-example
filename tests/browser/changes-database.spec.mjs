import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";

test("the article's console example creates, edits and rejects a stale version through real HTTP", async ({ page }) => {
  expect(process.env.BLOG_TEST_ADMIN_EMAIL).toBeTruthy();
  expect(process.env.BLOG_TEST_ADMIN_PASSWORD).toBeTruthy();
  await page.goto("/en/account");
  await page.getByLabel("Email", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_EMAIL);
  await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("link", { name: "Open editorial" })).toBeVisible();

  let id;
  // Capture the created identity even if a later assertion in the example fails.
  page.on("response", async response => {
    if (response.request().method() === "POST" && new URL(response.url()).pathname === "/service/editorial/posts" &&
        response.status() === 201) id = (await response.json()).data.id;
  });
  try {
    const script = readFileSync(new URL("../../docs/examples/post-changes.js", import.meta.url), "utf8");
    const result = await page.evaluate(script);
    id = result.id;
    expect(result.createdVersion).toBe(0);
    expect(result.savedVersion).toBe(1);
    expect(result.conflictStatus).toBe(409);
    await page.goto(result.preview);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("A safely updated post");
    await expect(page.getByText("Prepared, authorized, applied and validated.", { exact: true })).toBeVisible();
  } finally {
    if (id) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
      execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
        "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER, "-d", url.pathname.slice(1),
        "-c", `delete from public.blog_post where id = '${id}'`], {
        env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD }, stdio: ["ignore", "pipe", "pipe"]
      });
    }
  }
});
