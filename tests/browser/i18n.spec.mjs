import { test, expect } from "@playwright/test";

const id = "8a1c1582-e841-4a27-a506-1a630337df48";
const reply = (route, body, status = 200) => route.fulfill({
  status, contentType: "application/json", body: JSON.stringify(body)
});
const post = { id, version: 0, slug: "same-post", title: "An English article",
  content: "## Original content", contentFormat: "MARKDOWN", status: "PUBLISHED",
  publishedAt: "2026-09-28T08:00:00Z" };
async function publicApi(page) {
  await page.route("**/service/blog/posts**", route =>
    reply(route, new URL(route.request().url()).pathname === "/service/blog/posts"
      ? { rows: [{ data: post }], size: 15 } : { data: post }));
}
async function anonymous(page) {
  await page.route("**/service/auth/session", route => reply(route, { csrfToken: "locale-csrf" }));
}
test.beforeEach(({ page }) => {
  page.on("pageerror", error => { throw error; });
});

test("URL locale, placeholders, links, query, fragment and history stay together", async ({ page }, info) => {
  await publicApi(page);
  const suffix = "?q=Scala%20%26%20web&limit=5#posts";
  await page.goto("/de" + suffix);
  await expect(page.locator("html")).toHaveAttribute("lang", "de");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Von der Idee zur funktionierenden Software.");
  await expect(page.locator(".list-note")).toHaveText("1 von 15 Beiträgen");
  await expect(page.getByRole("link", { name: "Konto", exact: true })).toHaveAttribute("href", "/de/account");
  await expect(page.getByRole("link", { name: post.title })).toHaveAttribute("href", "/de/posts/same-post");
  await page.getByRole("button", { name: "Auf Englisch wechseln" }).click();
  await expect(page).toHaveURL("/en" + suffix);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.locator(".list-note")).toHaveText("Showing 1 of 15 posts");
  await expect(page.getByRole("link", { name: "Account", exact: true })).toHaveAttribute("href", "/en/account");
  await page.goBack();
  await expect(page.locator("html")).toHaveAttribute("lang", "de");
  await expect(page.getByRole("link", { name: "Konto", exact: true })).toHaveAttribute("href", "/de/account");
  await page.reload();
  await expect(page.locator(".list-note")).toHaveText("1 von 15 Beiträgen");
  await page.screenshot({ path: info.outputPath("german-list-desktop.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath("german-list-mobile.png") });
  await page.getByRole("link", { name: post.title }).click();
  await expect(page).toHaveURL("/de/posts/same-post");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(post.title);
  await expect(page.getByRole("heading", { level: 2 })).toHaveText("Original content");
  await page.reload();
  await expect(page.getByRole("link", { name: "Zu den neuesten Beiträgen" })).toBeVisible();
});

test("an unfinished search blocks language navigation until cleared", async ({ page }) => {
  await publicApi(page);
  await page.goto("/en");
  await page.getByLabel("Search posts", { exact: true }).fill("Not submitted");
  await expect(page.getByRole("button", { name: "Switch to German" })).toBeDisabled();
  await expect(page.locator("#language-switch-help")).toContainText("Finish or clear");
  await page.getByLabel("Search posts", { exact: true }).fill("");
  await page.getByRole("button", { name: "Switch to German" }).click();
  await expect(page.getByLabel("Beiträge suchen", { exact: true })).toHaveValue("");
});

test("German account navigation protects input and translates request errors", async ({ page }) => {
  await anonymous(page);
  await page.route("**/service/auth/login", route => reply(route, {}, 401));
  await page.goto("/de/account");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Dein Konto");
  await page.getByLabel("E-Mail", { exact: true }).fill("reader@example.test");
  await page.getByLabel("Passwort", { exact: true }).fill("incorrect passphrase");
  await expect(page.getByRole("button", { name: "Auf Englisch wechseln" })).toBeDisabled();
  await page.getByRole("button", { name: "Anmelden", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("E-Mail-Adresse oder Passwort ungültig.");
  await expect(page.getByLabel("E-Mail", { exact: true })).toHaveValue("reader@example.test");
  await expect(page.getByLabel("Passwort", { exact: true })).toHaveValue("");
  await page.getByLabel("E-Mail", { exact: true }).fill("");
  await page.getByRole("button", { name: "Auf Englisch wechseln" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Your account");
});

test("post edits survive blocked language changes; saved values reopen in German", async ({ page }, info) => {
  let saved = { ...post, status: "DRAFT" };
  const api = "/service/editorial/posts/" + id;
  await page.route("**/service/auth/session", route => reply(route, {
    csrfToken: "locale-csrf", account: { id: "admin", role: "ADMIN" }
  }));
  await page.route("**/service/editorial/authors**", route => reply(route, { rows: [], size: 0 }));
  await page.route("**/service/editorial/tags**", route => reply(route, { rows: [], size: 0 }));
  await page.route("**/service/editorial/posts/*", route => {
    if (route.request().method() === "PATCH") saved = { ...saved, ...route.request().postDataJSON(), version: saved.version + 1 };
    return reply(route, { data: saved, $links: [{ rel: "update", url: api, method: "PATCH" }] });
  });
  await page.goto("/en/editorial/posts/" + id + "/edit");
  await page.getByLabel("Title", { exact: true }).fill("Newer English content");
  await expect(page.getByRole("button", { name: "Switch to German" })).toBeDisabled();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Newer English content");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  await page.getByRole("button", { name: "Switch to German" }).click();
  await expect(page).toHaveURL("/de/editorial/posts/" + id + "/edit");
  await expect(page.getByLabel("Titel", { exact: true })).toHaveValue("Newer English content");
  await expect(page.getByRole("button", { name: "Fett", exact: true })).toBeVisible();
  await page.getByRole("textbox", { name: "content", exact: true }).click();
  await page.getByRole("button", { name: "Bild bearbeiten", exact: true }).click();
  const dialog = page.locator(".scalajs-ui-editor-dialog");
  await expect(dialog.getByLabel("Bildadresse", { exact: true })).toBeVisible();
  await expect(dialog.getByLabel("Alternativtext", { exact: true })).toBeVisible();
  await page.screenshot({ path: info.outputPath("german-editor-dialog.png") });
  await dialog.getByRole("button", { name: "Abbrechen", exact: true }).click();
  await page.reload();
  await expect(page.getByLabel("Titel", { exact: true })).toHaveValue("Newer English content");
});

test("a German recovery token is consumed from the fragment and cannot be lost to a language switch", async ({ page }) => {
  await anonymous(page);
  let sent;
  const token = "A".repeat(43);
  await page.route("**/service/auth/confirm", route => {
    sent = route.request().postDataJSON();
    return reply(route, { outcome: "completed" });
  });
  await page.goto("/de/confirm#token=" + token);
  await expect(page).toHaveURL("/de/confirm");
  await expect(page.getByRole("button", { name: "Auf Englisch wechseln" })).toBeDisabled();
  await page.getByLabel("Neues Passwort", { exact: true }).fill("a long confirmed passphrase");
  await page.getByRole("button", { name: "Passwort speichern" }).click();
  await expect(page.getByRole("status")).toHaveText("Dein Konto ist bereit. Du kannst dich jetzt anmelden.");
  expect(sent.token).toBe(token);
  await expect(page.getByRole("button", { name: "Auf Englisch wechseln" })).toBeDisabled();
  await page.getByRole("link", { name: "Zur Anmeldung" }).click();
  await page.getByRole("button", { name: "Auf Englisch wechseln" }).click();
  await expect(page).toHaveURL("/en/account");
  expect(page.url()).not.toContain(token);
});

test("German error pages keep the locale and unsupported locale URLs stay outside the shell", async ({ page, request }) => {
  await page.route("**/service/blog/posts/missing?locale=*", route => reply(route, {}, 404));
  await page.goto("/de/posts/missing");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Beitrag nicht gefunden");
  await expect(page.getByRole("link", { name: "Zu den neuesten Beiträgen" })).toHaveAttribute("href", "/de");
  expect((await request.get("/fr/posts/missing")).status()).toBe(404);
  const account = await request.get("/de/account");
  expect(account.status()).toBe(200);
  expect(account.headers()["cache-control"]).toBe("no-store");
});
