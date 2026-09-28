import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";
import { file } from "./media-fixture.mjs";

test.setTimeout(100_000);

test("upload, save, publish, retract and remove a cover through real HTTP and PostgreSQL", async ({ page, browser }, info) => {
  expect(process.env.BLOG_TEST_ADMIN_EMAIL).toBeTruthy();
  expect(process.env.BLOG_TEST_ADMIN_PASSWORD).toBeTruthy();
  const posts = new Set(), images = new Set();
  page.on("response", async response => {
    if (response.request().method() !== "POST" || response.status() !== 201) return;
    const path = new URL(response.url()).pathname;
    if (path === "/service/editorial/posts") posts.add((await response.json()).data.id);
    if (path === "/service/editorial/media") images.add((await response.json()).data.id);
  });
  const visitor = await browser.newContext({ baseURL: "http://127.0.0.1:18080" });
  const anonymous = await visitor.newPage();
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
    await expect(page.getByRole("link", { name: "Open editorial" })).toBeVisible();
    await page.goto("/en/editorial/new");
    const slug = "media-browser-" + randomUUID();
    await page.getByLabel("Title", { exact: true }).fill("A post with a cover image");
    await page.getByLabel("Slug", { exact: true }).fill(slug);
    await page.getByRole("textbox", { name: "content", exact: true }).pressSequentially("The image belongs to this post through an entity reference.");
    const uploaded = page.waitForResponse(value => value.request().method() === "POST" &&
      new URL(value.url()).pathname === "/service/editorial/media");
    await page.getByLabel("Cover image", { exact: true }).setInputFiles(file);
    const upload = await uploaded;
    expect(upload.status()).toBe(201);
    const media = (await upload.json()).data;
    images.add(media.id);
    const source = "/service/media/" + media.id;
    await expect(page.locator(".cover-preview")).toHaveAttribute("src", source);
    expect((await visitor.request.get(source)).status()).toBe(404);
    await page.getByLabel("Image description").fill("Blue and green test panels");
    const created = page.waitForResponse(value => value.request().method() === "POST" &&
      new URL(value.url()).pathname === "/service/editorial/posts");
    await page.getByRole("button", { name: "Save post", exact: true }).click();
    const saved = await created;
    expect(saved.status()).toBe(201);
    const post = (await saved.json()).data;
    posts.add(post.id);
    expect(post.coverImage.id).toBe(media.id);
    await expect(page).toHaveURL(new RegExp("/" + post.id + "/edit$"));
    await page.reload();
    await expect(page.locator(".cover-preview")).toHaveAttribute("src", source);
    await expect(page.getByLabel("Image description")).toHaveValue("Blue and green test panels");
    await page.getByRole("link", { name: "Open preview", exact: true }).click();
    await page.getByRole("button", { name: "Publish", exact: true }).click();
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Published");
    await anonymous.goto("/en/posts/" + slug);
    await expect(anonymous.locator(".post-cover")).toHaveAttribute("alt", "Blue and green test panels");
    expect(await anonymous.locator(".post-cover").evaluate(image => image.complete && image.naturalWidth === 480)).toBeTruthy();
    const publicRead = await visitor.request.get(source);
    expect(publicRead.status()).toBe(200);
    expect(publicRead.headers()["cache-control"]).toBe("no-store");
    await anonymous.screenshot({ path: info.outputPath("media-public.png"), fullPage: true });
    await page.getByRole("button", { name: "Retract", exact: true }).click();
    await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Draft");
    expect((await visitor.request.get(source)).status()).toBe(404);
    await page.goto(`/en/editorial/posts/${post.id}/edit`);
    await page.getByRole("button", { name: "Remove cover image" }).click();
    await page.getByRole("button", { name: "Save post", exact: true }).click();
    await expect(page.getByRole("status")).toHaveText("Saved.");
    await page.reload();
    await expect(page.locator(".cover-preview")).toHaveCount(0);
    // Removing a reference does not delete the upload; the owner can still read it.
    expect((await page.request.get(source)).status()).toBe(200);
  } finally {
    await visitor.close();
    const statements = [];
    for (const id of posts) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      statements.push(`delete from public.blog_post_tag where post_id = '${id}'`,
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
