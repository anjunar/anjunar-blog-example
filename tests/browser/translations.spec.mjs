import { test, expect } from "@playwright/test";

const post = { id: "post-id", version: 0, slug: "shared-slug", title: "Original title",
  summary: "English summary", content: "English paragraph", contentFormat: "PLAIN_TEXT",
  status: "PUBLISHED", publishedAt: "2026-09-29T10:00:00Z" };
const translated = { id: "translation-id", version: 0, locale: "de", title: "Deutscher Titel",
  content: "## Deutscher Inhalt", published: true };

test("route locale selects content without changing the slug and English remains available", async ({ page }) => {
  const locales = [];
  page.on("pageerror", error => { throw error; });
  await page.route("**/service/blog/posts/**", route => {
    const locale = new URL(route.request().url()).searchParams.get("locale");
    locales.push(locale);
    return route.fulfill({ json: { data: { ...post, contentLocale: locale, availableLocales: ["en", "de"],
      ...(locale === "de" ? { translation: translated } : {}) } } });
  });
  await page.goto("/de/posts/shared-slug");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Deutscher Titel");
  await expect(page.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "de");
  await expect(page.locator(".detail-summary")).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Deutscher Inhalt" })).toBeVisible();
  await page.getByRole("button", { name: "Auf Englisch wechseln" }).click();
  await expect(page).toHaveURL("/en/posts/shared-slug");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Original title");
  await expect(page.locator(".detail-summary")).toHaveText("English summary");
  await expect(page.locator(".post-content")).toContainText("English paragraph");
  expect(locales).toEqual(["de", "en"]);
});

test("a German page reports English fallback and marks the actual content language", async ({ page }) => {
  await page.route("**/service/blog/posts/**", route => route.fulfill({
    json: { data: { ...post, contentLocale: "en", availableLocales: ["en"] } }
  }));
  await page.goto("/de/posts/shared-slug");
  await expect(page.locator("html")).toHaveAttribute("lang", "de");
  await expect(page.locator(".translation-fallback")).toContainText("noch nicht veröffentlicht");
  await expect(page.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "en");
  await expect(page.locator(".detail-summary")).toHaveAttribute("lang", "en");
  await expect(page.locator(".post-content")).toHaveAttribute("lang", "en");
});
