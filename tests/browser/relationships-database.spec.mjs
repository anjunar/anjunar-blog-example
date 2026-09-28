import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";

// Earlier projects share this account. Honor its real five-logins-per-minute limit.
test.setTimeout(100_000);

async function signIn(page) {
  expect(process.env.BLOG_TEST_ADMIN_EMAIL).toBeTruthy();
  expect(process.env.BLOG_TEST_ADMIN_PASSWORD).toBeTruthy();
  await page.goto("/en/account");
  await page.getByLabel("Email", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_EMAIL);
  await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
  const attempt = async () => {
    // The UI clears password memory after every attempt, including a throttled one.
    await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
    const response = page.waitForResponse(value => value.request().method() === "POST" &&
      new URL(value.url()).pathname === "/service/auth/login");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    return response;
  };
  let response = await attempt();
  if (response.status() === 429) {
    await expect(page.getByRole("alert")).toContainText("Too many attempts");
    const seconds = Number(response.headers()["retry-after"] ?? "60");
    expect(seconds).toBeGreaterThan(0);
    expect(seconds).toBeLessThanOrEqual(60);
    await delay(seconds * 1000 + 250);
    response = await attempt();
  }
  expect(response.ok()).toBeTruthy();
  await expect(page.getByRole("link", { name: "Open editorial" })).toBeVisible();
}

function ownedRows(page) {
  const posts = new Set(), tags = new Set();
  page.on("response", async response => {
    if (response.request().method() !== "POST" || response.status() !== 201) return;
    const path = new URL(response.url()).pathname;
    if (path === "/service/editorial/posts") posts.add((await response.json()).data.id);
    if (path === "/service/editorial/tags") tags.add((await response.json()).data.id);
  });
  return () => {
    const statements = [];
    for (const id of posts) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      statements.push(`delete from public.blog_post_tag where post_id = '${id}'`,
        `delete from public.blog_post where id = '${id}'`);
    }
    for (const id of tags) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      statements.push(`delete from public.blog_tag where id = '${id}'`);
    }
    if (!statements.length) return;
    const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
    execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
      "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER,
      "-d", url.pathname.slice(1), "-c", "begin; " + statements.join("; ") + "; commit;"], {
      env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD }, stdio: ["ignore", "pipe", "pipe"]
    });
  };
}

test("the complete article example enforces reference semantics through HTTP and PostgreSQL", async ({ page }) => {
  await signIn(page);
  const cleanup = ownedRows(page);
  try {
    const source = readFileSync(new URL("../../docs/examples/entity-relationships.js", import.meta.url), "utf8");
    const result = await page.evaluate(source);
    expect(result).toMatchObject({ rejectedStatus: 400, createdVersion: 0, clearedVersion: 2, tagVersion: 1 });
    await page.goto(result.preview);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Shared entities, deliberate writes");
    await expect(page.locator(".post-author")).toHaveText("Editorial team");
  } finally { cleanup(); }
});

test("an administrator creates a tag, publishes its post, and clears references through the form", async ({ page }, info) => {
  await signIn(page);
  const cleanup = ownedRows(page);
  const suffix = randomUUID();
  const name = "Browser relationships " + suffix.slice(0, 8);
  const slug = "relationships-browser-" + suffix;
  try {
    await page.goto("/en/editorial/relationships");
    await page.getByLabel("New tag name", { exact: true }).fill(name);
    await page.getByLabel("New tag slug", { exact: true }).fill(slug);
    const tagResponse = page.waitForResponse(response => response.request().method() === "POST" &&
      new URL(response.url()).pathname === "/service/editorial/tags");
    await page.getByRole("button", { name: "Create tag", exact: true }).click();
    const tagReply = await tagResponse;
    expect(tagReply.status()).toBe(201);
    const createdTag = (await tagReply.json()).data;
    await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();

    await page.goto("/en/editorial/new");
    await page.getByLabel("Title", { exact: true }).fill("A post with shared metadata");
    await page.getByLabel("Slug", { exact: true }).fill(slug);
    await page.getByRole("textbox", { name: "content", exact: true }).pressSequentially("Authors and tags stay consistent from the form to PostgreSQL.");
    const session = await (await page.request.get("/service/auth/session")).json();
    await expect(page.getByRole("combobox", { name: "Author", exact: true }))
      .toContainText(session.account.displayName || "Unnamed author");
    const choices = page.getByRole("combobox", { name: "Tags", exact: true });
    await choices.click();
    await page.locator(".ui-combo-box__item").filter({ hasText: name }).click();
    await choices.press("Escape");
    const createResponse = page.waitForResponse(response => response.request().method() === "POST" &&
      new URL(response.url()).pathname === "/service/editorial/posts");
    await page.getByRole("button", { name: "Save post", exact: true }).click();
    const postReply = await createResponse;
    expect(postReply.status()).toBe(201);
    const created = (await postReply.json()).data;
    expect(created.author.id).toBe(session.account.id);
    expect(created.author).not.toHaveProperty("email");
    expect(created.tags.map(tag => tag.id)).toEqual([createdTag.id]);
    await expect(page).toHaveURL(new RegExp("/" + created.id + "/edit$"));
    await page.reload();
    await expect(choices).toContainText(name);
    await page.getByRole("link", { name: "Open preview", exact: true }).click();
    await page.getByRole("button", { name: "Publish", exact: true }).click();
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Published");
    await page.getByRole("link", { name: "Open public post", exact: true }).click();
    await expect(page.locator(".post-tag")).toHaveText(name);
    await expect(page.locator(".post-author")).toHaveText(session.account.displayName || "Editorial team");
    await page.screenshot({ path: info.outputPath("relationships-public.png"), fullPage: true });

    await page.goto(`/en/editorial/posts/${created.id}/edit`);
    await page.getByRole("button", { name: "Clear author", exact: true }).click();
    await page.getByRole("button", { name: "Clear tags", exact: true }).click();
    await page.getByRole("button", { name: "Save post", exact: true }).click();
    await expect(page.getByRole("status")).toHaveText("Saved.");
    await page.reload();
    await expect(page.getByRole("combobox", { name: "Author", exact: true })).toContainText("Choose an author");
    await expect(choices).toContainText("Choose tags");
    const catalog = await (await page.request.get("/service/editorial/tags?limit=100")).json();
    expect(catalog.rows.some(row => row.data.id === createdTag.id)).toBeTruthy();
    await page.goto("/en/editorial/relationships");
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: info.outputPath("relationships-catalog-mobile.png"), fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  } finally { cleanup(); }
});
