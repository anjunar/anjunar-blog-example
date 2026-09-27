import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";

// This suite owns one draft in the dedicated test database and deletes it in finally.
function sql(statement) {
  const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
  execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
    "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER,
    "-d", url.pathname.slice(1), "-c", statement], {
    env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD }, stdio: ["ignore", "pipe", "pipe"]
  });
}

test("real administrator previews, publishes and retracts an isolated draft", async ({ page, context }, testInfo) => {
  expect(process.env.BLOG_TEST_ADMIN_EMAIL).toBeTruthy();
  expect(process.env.BLOG_TEST_ADMIN_PASSWORD).toBeTruthy();
  const id = randomUUID();
  const slug = `editorial-browser-${id}`;
  sql(`insert into public.blog_post (id,version,slug,title,content,status)
    values ('${id}',0,'${slug}','A publication walkthrough','A private draft becomes a public post.','DRAFT')`);
  try {
    await page.goto("/en/account");
    await page.getByLabel("Email", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_EMAIL);
    await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.getByRole("link", { name: "Open editorial" })).toBeVisible();
    await page.goto(`/en/editorial/posts/${id}`);
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Draft");
    expect((await context.request.get(`/service/blog/posts/${slug}`)).status()).toBe(404);
    await page.getByRole("button", { name: "Publish", exact: true }).click();
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Published");
    const published = await context.request.get(`/service/blog/posts/${slug}`);
    expect(published.status()).toBe(200);
    expect((await published.json()).data.version).toBe(1);
    await page.screenshot({ path: testInfo.outputPath("real-editorial-published.png"), fullPage: true });
    await page.reload();
    await expect(page.getByRole("button", { name: "Retract" })).toBeVisible();
    await page.getByRole("button", { name: "Retract" }).click();
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Draft");
    expect((await context.request.get(`/service/blog/posts/${slug}`)).status()).toBe(404);
    const preview = await context.request.get(`/service/editorial/posts/${id}`);
    expect((await preview.json()).data.version).toBe(2);
  } finally { sql(`delete from public.blog_post where id = '${id}'`); }
});
