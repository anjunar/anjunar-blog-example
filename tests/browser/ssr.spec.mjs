import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";

const ids = Array.from({ length: 4 }, () => randomUUID());
const prefix = "ssr-" + ids[0];
const translated = prefix + "-translated";
const fallback = prefix + "-fallback";
const draft = prefix + "-draft";
const title = 'Server <script>alert("unsafe")</script> & reader';
const body = "## Shared document\n\nAlready readable without JavaScript.\n\n```scala\nval answer = 42\n```";
const quoted = value => "'" + value.replaceAll("'", "''") + "'";

function sql(statement) {
  const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
  execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
    "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER,
    "-d", url.pathname.slice(1), "-c", statement], {
    env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD },
    stdio: ["ignore", "pipe", "pipe"],
  });
}

test.beforeAll(() => {
  sql("begin; insert into public.blog_post " +
    "(id, version, slug, title, content, content_format, summary, status, published_at) values " +
    [ [ids[0], translated, title, "PUBLISHED"], [ids[1], fallback, "English fallback", "PUBLISHED"],
      [ids[2], draft, "Private SSR draft", "DRAFT"] ].map(([id, slug, heading, status]) =>
      "(" + [quoted(id), "0", quoted(slug), quoted(heading), quoted(body), "'MARKDOWN'", "'Source summary'",
        quoted(status), status === "PUBLISHED" ? "now()" : "null"].join(",") + ")").join(",") + ";" +
    "insert into public.blog_post_translation (id,version,post_id,locale,title,content,published) values (" +
    [quoted(ids[3]), "0", quoted(ids[0]), "'de'", "'Ein Artikel vom Server'",
      quoted("## Gemeinsames Dokument\n\nSchon ohne JavaScript lesbar."), "true"].join(",") + "); commit;");
});

test.afterAll(() => {
  sql("begin; delete from public.blog_post_translation where id = " + quoted(ids[3]) +
    "; delete from public.blog_post where id in (" + ids.slice(0, 3).map(quoted).join(",") + "); commit;");
});

test("list, query and pagination are present before JavaScript runs", async ({ page }) => {
  const response = await page.goto("/en?q=" + prefix + "&sort=title&limit=1");
  expect(response.status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.locator(".post")).toHaveCount(1);
  await expect(page.getByRole("link", { name: "English fallback", exact: true })).toBeVisible();
  await expect(page.locator(".list-note")).toHaveText("Showing 1 of 2 posts");
  await page.getByRole("link", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("link", { name: title, exact: true })).toBeVisible();
  await expect(page.getByText("Private SSR draft", { exact: true })).toHaveCount(0);
});

test("the same detail component renders escaped titles and structured Markdown", async ({ page }, info) => {
  const response = await page.goto("/en/posts/" + translated);
  expect(response.status()).toBe(200);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
  await expect(page.getByRole("heading", { name: "Shared document", exact: true })).toBeVisible();
  await expect(page.locator(".post-content pre")).toContainText("val answer = 42");
  expect(await response.text()).not.toContain('<script>alert("unsafe")</script>');
  await expect(page.locator('link[rel="stylesheet"]')).toHaveCount(2);
  await page.screenshot({ path: info.outputPath("ssr-english.png"), fullPage: true });
});

test("German UI, published translation and whole-article fallback agree", async ({ page }, info) => {
  expect((await page.goto("/de/posts/" + translated)).status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "de");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Ein Artikel vom Server");
  await expect(page.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "de");
  await expect(page.getByRole("heading", { name: "Gemeinsames Dokument" })).toBeVisible();
  await expect(page.locator(".detail-summary")).toHaveCount(0);
  await page.screenshot({ path: info.outputPath("ssr-german.png"), fullPage: true });
  await page.goto("/de/posts/" + fallback);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("English fallback");
  await expect(page.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "en");
  await expect(page.locator(".translation-fallback")).toContainText("noch nicht veröffentlicht");
  await expect(page.locator(".detail-summary")).toHaveText("Source summary");
  await page.goto("/de?q=" + encodeURIComponent("Ein Artikel vom Server"));
  await expect(page.getByRole("link", { name: "Ein Artikel vom Server", exact: true })).toBeVisible();
});

test("document status, HEAD, private shell and asset boundaries stay correct", async ({ page, request }) => {
  for (const slug of [draft, prefix + "-unknown"]) {
    expect((await page.goto("/en/posts/" + slug)).status()).toBe(404);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Post not found");
    const head = await request.head("/en/posts/" + slug);
    expect(head.status()).toBe(404);
    expect(await head.text()).toBe("");
  }
  expect((await page.goto("/de?limit=0")).status()).toBe(400);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Ungültige Seite");
  expect((await request.get("/de/unavailable")).status()).toBe(503);
  expect((await request.get("/ssr/main.js")).status()).toBe(404);
  expect((await request.post("/en/posts/" + translated)).status()).toBe(405);
  const privatePage = await request.get("/de/editorial/new");
  expect(privatePage.status()).toBe(200);
  expect(privatePage.headers()["cache-control"]).toBe("no-store");
  expect(await privatePage.text()).toContain('<div id="app"></div>');
  const publicPage = await request.get("/de/posts/" + translated, {
    headers: { Cookie: "BLOGSESSIONID=untrusted-session" },
  });
  expect(publicPage.status()).toBe(200);
  expect(publicPage.headers()["cache-control"]).toBe("no-store");
  expect(publicPage.headers()["set-cookie"]).toBeUndefined();
});

test.describe("browser takeover", () => {
  test.use({ javaScriptEnabled: true });
  test("remounts one page and keeps normal client navigation and controls", async ({ page }) => {
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    const loaded = page.waitForResponse(response => response.url().includes("/service/blog/posts?"));
    const document = await page.goto("/en?q=" + prefix);
    expect(await document.text()).toContain("English fallback");
    await loaded;
    await expect(page.locator(".blog")).toHaveCount(1);
    const toggle = page.getByRole("button", { name: "Show summaries", exact: true });
    await toggle.click();
    await expect(toggle).toHaveAttribute("aria-pressed", "false");
    await page.getByRole("link", { name: title, exact: true }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
    await page.getByRole("button", { name: "Switch to German" }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Ein Artikel vom Server");
    expect(errors).toEqual([]);
  });
});
