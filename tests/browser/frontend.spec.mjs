import { test, expect } from "@playwright/test";

let runtimeErrors;

test.beforeEach(({ page }) => {
  runtimeErrors = [];
  page.on("pageerror", error => runtimeErrors.push(error.message));
  page.on("console", message => {
    if (message.type() === "error") runtimeErrors.push(message.text());
  });
});

test.afterEach(() => {
  expect(runtimeErrors).toEqual([]);
});

test("the linked Scala.js page renders semantic post previews", async ({ page }, testInfo) => {
  await page.goto("/");
  await expect(page).toHaveTitle("Anjunar Journal");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByRole("main")).toBeVisible();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("From idea to working software.");
  await expect(page.getByRole("navigation", { name: "Main navigation" })).toBeVisible();
  await expect(page.getByRole("article")).toHaveCount(3);
  await expect(page.getByRole("heading", { level: 3 })).toHaveText([
    "Serving posts through REST",
    "One model, two jobs",
    "Room for the model to grow",
  ]);
  await expect(page.locator(".post-date")).toHaveText(["2026-09-27", "2026-09-26", "2026-09-25"]);
  await expect(page.locator(".post-summary")).toHaveCount(3);
  await page.screenshot({ path: testInfo.outputPath("desktop.png"), fullPage: true });
});

test("keyboard controls update conditions and list order without duplicating rows", async ({ page }) => {
  await page.goto("/");
  const summaries = page.getByRole("button", { name: "Show summaries" });
  await expect(summaries).toHaveAttribute("aria-pressed", "true");
  await summaries.focus();
  await page.keyboard.press("Space");
  await expect(summaries).toBeFocused();
  await expect(summaries).toHaveAttribute("aria-pressed", "false");
  await expect(page.locator(".post-summary")).toHaveCount(0);

  await page.getByRole("button", { name: "Sort oldest first" }).click();
  await expect(page.getByRole("heading", { level: 3 }).first()).toHaveText("Room for the model to grow");
  await expect(page.getByRole("button", { name: "Sort newest first" })).toHaveText("Oldest first");
  await expect(page.locator(".post-summary")).toHaveCount(0);
  await page.getByRole("button", { name: "Sort newest first" }).click();
  await expect(page.getByRole("heading", { level: 3 }).first()).toHaveText("Serving posts through REST");

  await summaries.focus();
  await page.keyboard.press("Enter");
  await expect(summaries).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".post-summary")).toHaveCount(3);
  await expect(page.getByRole("article")).toHaveCount(3);
});

test("the mobile page fits the viewport and its skip link moves keyboard focus", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(page.getByRole("article")).toHaveCount(3);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  for (const button of await page.getByRole("button").all()) {
    const bounds = await button.boundingBox();
    expect(bounds.height).toBeGreaterThanOrEqual(44);
  }
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to content" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath("mobile.png"), fullPage: true });
});

test("static pages remain separate from the REST API and unknown paths", async ({ request }) => {
  const index = await request.get("/");
  expect(index.status()).toBe(200);
  expect(index.headers()["content-type"]).toContain("text/html");
  expect(index.headers()["cache-control"]).toBe("no-cache");
  expect(await index.text()).toContain('type="module" src="/main.js"');
  const script = await request.get("/main.js");
  expect(script.status()).toBe(200);
  expect(script.headers()["content-type"]).toMatch(/javascript/);
  const style = await request.get("/style.css");
  expect(style.status()).toBe(200);
  expect(style.headers()["content-type"]).toContain("text/css");
  const live = await request.get("/service/health/live");
  expect(live.status()).toBe(200);
  expect(await live.text()).toBe("UP\n");
  for (const path of ["/service/no-such-resource", "/service-other", "/no-such-page", "/.env", "/build.sbt"]) {
    expect((await request.get(path)).status(), path).toBe(404);
  }
  const head = await request.head("/");
  expect(head.status()).toBe(200);
  expect(await head.body()).toHaveLength(0);
  expect((await request.post("/")).status()).toBe(405);
});
