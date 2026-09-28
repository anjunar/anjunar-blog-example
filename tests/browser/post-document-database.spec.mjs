import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";
import { file } from "./media-fixture.mjs";

test.setTimeout(120_000);
test.use({ actionTimeout: 10_000 });

test("write formatted content, upload an inline image, save, publish and retract", async ({ page, browser }, info) => {
  const posts = new Set(), images = new Set(), errors = [];
  page.on("pageerror", error => errors.push(error.message));
  page.on("response", async response => {
    if (response.request().method() !== "POST" || response.status() !== 201) return;
    const path = new URL(response.url()).pathname;
    if (path === "/service/editorial/posts") posts.add((await response.json()).data.id);
    if (path === "/service/editorial/media") images.add((await response.json()).data.id);
  });
  const visitor = await browser.newContext({ baseURL: "http://127.0.0.1:18080" });
  try {
    await page.goto("/en/account");
    await page.getByLabel("Email", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_EMAIL);
    const signIn = async () => {
      await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
      const response = page.waitForResponse(value => value.request().method() === "POST" &&
        new URL(value.url()).pathname === "/service/auth/login");
      await page.getByRole("button", { name: "Sign in", exact: true }).click();
      return response;
    };
    let login = await signIn();
    if (login.status() === 429) {
      const seconds = Number(login.headers()["retry-after"] ?? "60");
      expect(seconds).toBeGreaterThan(0); expect(seconds).toBeLessThanOrEqual(60);
      await delay(seconds * 1000 + 250);
      login = await signIn();
    }
    expect(login.ok()).toBeTruthy();
    await page.goto("/en/editorial/new");
    const slug = "document-browser-" + randomUUID();
    await page.getByLabel("Title", { exact: true }).fill("Writing a post with the editor");
    await page.getByLabel("Slug", { exact: true }).fill(slug);
    const surface = page.getByRole("textbox", { name: "content", exact: true });
    await surface.click();
    await surface.pressSequentially("A formatted introduction.");
    await surface.press("ControlOrMeta+Home");
    await surface.press("ControlOrMeta+Shift+End");
    await page.getByRole("button", { name: "Bold", exact: true }).click();
    await expect(surface.locator("strong")).toHaveText("A formatted introduction.");
    await page.getByRole("button", { name: "Edit Markdown", exact: true }).click();
    const source = page.locator(".scalajs-ui-editor__markdown-textarea");
    await expect(source).toHaveValue(/\*\*A formatted introduction\.\*\*/);
    await source.fill("## A real document\n\n**A formatted introduction.**\n\n```scala\nval answer = 42\nprintln(answer)\n```\n\nAn image follows.");
    await page.getByRole("button", { name: "Visual editor", exact: true }).click();
    await expect(surface.locator("pre")).toContainText("val answer = 42");
    await surface.click();
    await surface.press("ControlOrMeta+End");
    await surface.press("Enter");
    const uploaded = page.waitForResponse(value => value.request().method() === "POST" &&
      new URL(value.url()).pathname === "/service/editorial/media");
    const chooser = page.waitForEvent("filechooser");
    await page.getByRole("button", { name: "Upload image", exact: true }).click();
    await (await chooser).setFiles(file);
    const upload = await uploaded;
    expect(upload.status()).toBe(201);
    const media = (await upload.json()).data;
    images.add(media.id);
    const url = "/service/media/" + media.id;
    const image = surface.locator("img");
    await expect(image).toHaveAttribute("src", url);
    expect((await visitor.request.get(url)).status()).toBe(404);
    await image.click();
    await page.getByRole("button", { name: "Edit image", exact: true }).click();
    const dialog = page.locator(".scalajs-ui-editor-dialog");
    await dialog.getByLabel("Alternative text", { exact: true }).fill("Blue and green panels inside the post");
    await page.screenshot({ path: info.outputPath("document-image-dialog.png") });
    await dialog.getByRole("button", { name: "Apply", exact: true }).click();
    await expect(image).toHaveAttribute("alt", "Blue and green panels inside the post");
    const created = page.waitForResponse(value => value.request().method() === "POST" &&
      new URL(value.url()).pathname === "/service/editorial/posts");
    await page.getByRole("button", { name: "Save post", exact: true }).click();
    const response = await created;
    expect(response.status()).toBe(201);
    const post = (await response.json()).data;
    posts.add(post.id);
    expect(post.contentFormat).toBe("MARKDOWN");
    expect(post.content).toContain(url);
    expect(post).not.toHaveProperty("inlineMedia");
    await expect(page).toHaveURL(new RegExp("/" + post.id + "/edit$"));
    await page.reload();
    await expect(surface.locator("h2")).toHaveText("A real document");
    await expect(surface.locator("pre")).toContainText("println(answer)");
    await expect(surface.locator("img")).toHaveAttribute("alt", "Blue and green panels inside the post");
    await surface.scrollIntoViewIfNeeded();
    await page.screenshot({ path: info.outputPath("document-editor-desktop.png") });
    await page.getByRole("link", { name: "Open preview", exact: true }).click();
    await page.getByRole("button", { name: "Publish", exact: true }).click();
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Published");
    const publicPage = await visitor.newPage();
    await publicPage.goto("/en/posts/" + slug);
    const document = publicPage.locator(".post-content");
    await expect(document.locator("h2")).toHaveText("A real document");
    await expect(document.locator("strong")).toHaveText("A formatted introduction.");
    await expect(document.locator("pre")).toContainText("val answer = 42");
    await expect(document.locator("img")).toHaveAttribute("src", url);
    await expect.poll(() => document.locator("img").evaluate(image => image.complete && image.naturalWidth === 480)).toBe(true);
    await expect(document.getByRole("toolbar")).toHaveCount(0);
    await expect(document.locator("[contenteditable=true]")).toHaveCount(0);
    await publicPage.screenshot({ path: info.outputPath("document-public.png"), fullPage: true });
    await page.getByRole("button", { name: "Retract", exact: true }).click();
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Draft");
    expect((await visitor.request.get(url)).status()).toBe(404);
    expect(errors).toEqual([]);
  } finally {
    await visitor.close();
    const statements = [];
    for (const id of posts) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      statements.push(`delete from public.blog_post_media where post_id = '${id}'`,
        `delete from public.blog_post_tag where post_id = '${id}'`,
        `delete from public.blog_post where id = '${id}'`);
    }
    for (const id of images) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      statements.push(`delete from public.blog_media where id = '${id}'`);
    }
    if (statements.length) {
      const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
      execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
        "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER,
        "-d", url.pathname.slice(1), "-c", "begin; " + statements.join("; ") + "; commit;"], {
        env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD }, stdio: ["ignore", "pipe", "pipe"]
      });
    }
  }
});
