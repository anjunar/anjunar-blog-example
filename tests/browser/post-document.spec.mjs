import { test, expect } from "@playwright/test";

const id = "8a1c1582-e841-4a27-a506-1a630337df48";
const api = `/service/editorial/posts/${id}`;
const edit = `/en/editorial/posts/${id}/edit`;
const reply = (route, body, status = 200) => route.fulfill({
  status, contentType: status >= 400 ? "application/problem+json" : "application/json",
  body: JSON.stringify(body)
});
const envelope = post => ({ data: post, $links: [{ rel: "update", url: api, method: "PATCH" }] });
const initial = (content, contentFormat) => ({ id, version: 0, slug: "document", title: "A document",
  content, contentFormat: contentFormat ?? null, status: "DRAFT" });
async function session(page) {
  await page.route("**/service/auth/session", route => reply(route, {
    csrfToken: "document-token", account: { id: "admin", role: "ADMIN" }
  }));
  await page.route("**/service/editorial/authors**", route => reply(route, { rows: [], size: 0 }));
  await page.route("**/service/editorial/tags**", route => reply(route, { rows: [], size: 0 }));
}

test("legacy text stays literal until explicit conversion and survives a reload", async ({ page }) => {
  await session(page);
  const literal = "*literal*\n# heading\n<example> &copy;\n![sample](/service/media/not-an-image)";
  let post = initial(literal);
  let payload;
  await page.route("**/service/editorial/posts/*", route => {
    if (route.request().method() === "PATCH") {
      payload = route.request().postDataJSON();
      post = { ...post, ...payload, version: post.version + 1 };
    }
    return reply(route, envelope(post));
  });
  await page.goto(edit);
  await expect(page.getByLabel("Content", { exact: true })).toHaveValue(literal);
  await expect(page.getByRole("toolbar")).toHaveCount(0);
  await page.getByRole("button", { name: "Enable rich text" }).click();
  const surface = page.getByRole("textbox", { name: "content", exact: true });
  await expect(surface).toContainText("*literal*");
  await expect(surface.locator("strong, h1, img, example")).toHaveCount(0);
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(payload.contentFormat).toBe("MARKDOWN");
  expect(payload.content).toContain("\\*literal\\*");
  await page.reload();
  await expect(surface).toContainText("<example> &copy;");
  await expect(surface.locator("strong, h1, img, example")).toHaveCount(0);
});

test("Markdown binding keeps newer typing across a delayed save and reports document errors", async ({ page }, info) => {
  await session(page);
  let release;
  const gate = new Promise(resolve => release = resolve);
  const writes = [];
  let post = initial("Original paragraph", "MARKDOWN");
  await page.route("**/service/editorial/posts/*", async route => {
    if (route.request().method() === "PATCH") {
      const body = route.request().postDataJSON();
      writes.push(body);
      if (writes.length === 1) await gate;
      if (body.content.includes("<script>")) return reply(route, {
        status: 400, title: "Invalid document", errors: [{ path: ["content"], message: "Use Markdown; raw HTML is not supported." }]
      }, 400);
      post = { ...post, ...body, version: post.version + 1 };
    }
    return reply(route, envelope(post));
  });
  await page.goto(edit);
  await page.getByRole("button", { name: "Edit Markdown", exact: true }).click();
  const source = page.locator(".scalajs-ui-editor__markdown-textarea");
  await source.fill("## Submitted\n\nA **formatted** paragraph.");
  const started = page.waitForRequest(request => request.method() === "PATCH");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await started;
  await source.fill("## Newer typing\n\nA **formatted** paragraph.");
  release();
  await expect(page.getByRole("status")).toHaveText("Saved. Your newer edits still need saving.");
  await expect(source).toHaveValue("## Newer typing\n\nA **formatted** paragraph.");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(writes[1].version).toBe(1);
  await source.fill("<script>alert(1)</script>");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.locator("#post-content-errors")).toContainText("raw HTML is not supported");
  await expect(source).toHaveValue("<script>alert(1)</script>");
  await source.fill("## Corrected\n\nA **formatted** paragraph.");
  await page.getByRole("button", { name: "Visual editor", exact: true }).click();
  const surface = page.getByRole("textbox", { name: "content", exact: true });
  await expect(surface.locator("h2")).toHaveText("Corrected");
  await expect(surface.locator("strong")).toHaveText("formatted");
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await surface.scrollIntoViewIfNeeded();
  await page.screenshot({ path: info.outputPath("document-editor-mobile.png") });
});

test("public Markdown renders code literally and refuses external images", async ({ page }) => {
  const requests = [];
  await page.route("https://untrusted.example/**", route => { requests.push(route.request().url()); return route.abort(); });
  await page.route("**/service/blog/posts/document?locale=en", route => reply(route, { data: {
    ...initial("## A heading\n\n**Bold text**\n\n```html\n<script>alert(1)</script>\n```\n\n![Private](https://untrusted.example/private.png)", "MARKDOWN"),
    status: "PUBLISHED", publishedAt: "2026-09-28T00:00:00Z"
  }}));
  await page.goto("/en/posts/document");
  const document = page.locator(".post-content");
  await expect(document.getByRole("heading", { level: 2 })).toHaveText("A heading");
  await expect(document.locator("pre")).toContainText("<script>alert(1)</script>");
  await expect(document.locator("script, img, [contenteditable=true]")).toHaveCount(0);
  await expect(document.getByRole("toolbar")).toHaveCount(0);
  expect(requests).toEqual([]);
});
