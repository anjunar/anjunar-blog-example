import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";

test.setTimeout(120_000);
test.use({ actionTimeout: 10_000 });

test("German draft, independent publication, localized search, conflict and fallback", async ({ page, browser }, info) => {
  let id;
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  const visitor = await browser.newContext({ baseURL: "http://127.0.0.1:18080" });
  const slug = "translation-browser-" + randomUUID();
  try {
    await page.goto("/en/account");
    await page.getByLabel("Email", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_EMAIL);
    const login = async () => {
      await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
      const response = page.waitForResponse(value => new URL(value.url()).pathname === "/service/auth/login");
      await page.getByRole("button", { name: "Sign in", exact: true }).click();
      return response;
    };
    let signedIn = await login();
    if (signedIn.status() === 429) {
      await delay(Number(signedIn.headers()["retry-after"] ?? "60") * 1000 + 250);
      signedIn = await login();
    }
    expect(signedIn.ok()).toBeTruthy();
    const session = await (await page.request.get("/service/auth/session")).json();
    const headers = { "X-CSRF-Token": session.csrfToken };
    const created = await page.request.post("/service/editorial/posts", { headers,
      data: { slug, title: "A source for both languages", summary: "English summary stays English",
        content: "## Original\n\nAn unchanged English paragraph.", contentFormat: "MARKDOWN" } });
    expect(created.status()).toBe(201);
    id = (await created.json()).data.id;
    expect((await page.request.post("/service/editorial/posts/" + id + "/publish", { headers })).ok()).toBeTruthy();
    await page.goto("/en/editorial/posts/" + id);
    await page.getByRole("link", { name: "German translation", exact: true }).click();
    await expect(page).toHaveURL("/en/editorial/posts/" + id + "/translations/de");
    await expect(page.getByLabel("Title", { exact: true })).toHaveValue("");
    await expect(page.locator(".translation-source")).toContainText("An unchanged English paragraph.");
    await page.getByLabel("Title", { exact: true }).fill("Eine gemeinsame Anwendung");
    await expect(page.getByRole("button", { name: "Switch to German" })).toBeDisabled();
    await page.getByRole("button", { name: "Edit Markdown", exact: true }).click();
    await page.locator(".scalajs-ui-editor__markdown-textarea").fill("## Übersetzt\n\nEin eigenständiger deutscher Absatz.");
    await page.getByRole("button", { name: "Save translation", exact: true }).click();
    await expect(page.getByRole("button", { name: "Publish translation", exact: true })).toBeEnabled();
    await page.reload();
    await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Eine gemeinsame Anwendung");
    const publicPage = await visitor.newPage();
    await publicPage.goto("/de/posts/" + slug);
    await expect(publicPage.getByRole("heading", { level: 1 })).toHaveText("A source for both languages");
    await expect(publicPage.locator(".translation-fallback")).toContainText("noch nicht veröffentlicht");
    await expect(publicPage.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "en");
    await page.getByRole("button", { name: "Publish translation", exact: true }).click();
    await expect(page.getByRole("button", { name: "Retract translation", exact: true })).toBeEnabled();
    await publicPage.reload();
    await expect(publicPage.getByRole("heading", { level: 1 })).toHaveText("Eine gemeinsame Anwendung");
    await expect(publicPage.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "de");
    await expect(publicPage.locator(".detail-summary")).toHaveCount(0);
    await expect(publicPage.getByRole("heading", { name: "Übersetzt", exact: true })).toBeVisible();
    await expect(publicPage.locator(".translation-fallback")).toHaveCount(0);
    await publicPage.screenshot({ path: info.outputPath("translated-post.png"), fullPage: true });
    await publicPage.goto("/de?q=gemeinsame&sort=title");
    await expect(publicPage.getByRole("link", { name: "Eine gemeinsame Anwendung", exact: true })).toHaveAttribute("href", "/de/posts/" + slug);
    await page.getByRole("button", { name: "Switch to German" }).click();
    await expect(page).toHaveURL("/de/editorial/posts/" + id + "/translations/de");
    await expect(page.getByLabel("Titel", { exact: true })).toHaveValue("Eine gemeinsame Anwendung");
    await page.screenshot({ path: info.outputPath("translation-editor-desktop.png"), fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: info.outputPath("translation-editor-mobile.png"), fullPage: true });

    const saved = (await (await page.request.get("/service/editorial/posts/" + id + "/translations/de")).json()).data;
    const api = "/service/editorial/posts/" + id + "/translations/" + saved.id;
    const changed = await page.request.patch(api, { headers, data: { version: saved.version, title: "Extern geänderter Titel" } });
    expect(changed.ok()).toBeTruthy();
    await page.getByLabel("Titel", { exact: true }).fill("Meine ungespeicherte Änderung");
    await page.getByRole("button", { name: "Übersetzung speichern", exact: true }).click();
    await expect(page.getByText("Die Übersetzung wurde geändert. Lade sie vor dem erneuten Speichern neu.", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Titel", { exact: true })).toHaveValue("Meine ungespeicherte Änderung");
    await page.getByRole("button", { name: "Änderungen verwerfen und neu laden", exact: true }).click();
    await expect(page.getByLabel("Titel", { exact: true })).toHaveValue("Extern geänderter Titel");
    await page.getByRole("button", { name: "Übersetzung zurückziehen", exact: true }).click();
    await expect(page.getByRole("button", { name: "Übersetzung veröffentlichen", exact: true })).toBeEnabled();
    await publicPage.goto("/de/posts/" + slug);
    await expect(publicPage.getByRole("heading", { level: 1 })).toHaveText("A source for both languages");
    const source = (await (await page.request.get("/service/editorial/posts/" + id)).json()).data;
    expect(source.title).toBe("A source for both languages");
    expect(source.content).toContain("An unchanged English paragraph.");
    expect(source.version).toBe(1);
    expect(errors).toEqual([]);
  } finally {
    await visitor.close();
    if (id) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
      const statements = [
        "delete from public.blog_post_translation_media where translation_id in (select id from public.blog_post_translation where post_id = '" + id + "')",
        "delete from public.blog_post_translation where post_id = '" + id + "'",
        "delete from public.blog_post where id = '" + id + "'"
      ];
      execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
        "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER,
        "-d", url.pathname.slice(1), "-c", "begin; " + statements.join("; ") + "; commit;"], {
        env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD }, stdio: ["ignore", "pipe", "pipe"]
      });
    }
  }
});
