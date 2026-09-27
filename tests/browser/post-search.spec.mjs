import { test, expect } from "@playwright/test";

const link = (rel, url, method = "GET") => ({ rel, url, method });
const row = (title, id = "search-post") => ({ data: {
  id, version: 0, slug: id, title, summary: "A matching summary",
  status: "PUBLISHED", publishedAt: "2026-09-27T10:00:00Z"
} });
const reply = (route, body, status = 200) =>
  route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

async function publicSearch(page, requests = []) {
  await page.route("**/service/blog/posts?**", route => {
    const query = new URL(route.request().url()).searchParams;
    requests.push(Object.fromEntries(query));
    const offset = Number(query.get("offset"));
    const text = query.get("q") ?? "";
    return reply(route, { size: text === "missing" ? 0 : 31,
      rows: text === "missing" || offset >= 31 ? [] : [row((text || "All posts") + " / " + offset)] });
  });
}

test("search submits all controls, resets the page and restores them through history", async ({ page }, testInfo) => {
  const requests = [];
  await publicSearch(page, requests);
  await page.goto("/en?offset=20");
  await page.getByLabel("Search posts", { exact: true }).fill("  Scala & SQL  ");
  await page.getByLabel("Sort by").selectOption("title");
  await page.getByLabel("Posts per page").selectOption("10");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByRole("heading", { level: 3 })).toHaveText("Scala & SQL / 0");
  const address = new URL(page.url());
  expect(address.searchParams.get("q")).toBe("Scala & SQL");
  expect(address.searchParams.has("offset")).toBe(false);
  expect(requests.at(-1)).toMatchObject({ offset: "0", limit: "10", q: "Scala & SQL", sort: "title" });
  await page.getByRole("link", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("heading", { level: 3 })).toHaveText("Scala & SQL / 10");
  expect(new URL(page.url()).searchParams.get("sort")).toBe("title");
  await page.goBack();
  await expect(page.getByRole("heading", { level: 3 })).toHaveText("Scala & SQL / 0");
  await expect(page.getByLabel("Search posts", { exact: true })).toHaveValue("Scala & SQL");
  await expect(page.getByLabel("Sort by")).toHaveValue("title");
  await expect(page.getByLabel("Posts per page")).toHaveValue("10");
  await page.goForward();
  await expect(page.getByRole("heading", { level: 3 })).toHaveText("Scala & SQL / 10");
  await page.reload();
  await expect(page.getByLabel("Search posts", { exact: true })).toHaveValue("Scala & SQL");
  await page.screenshot({ path: testInfo.outputPath("search-desktop.png"), fullPage: true });
});

test("empty filtered pages return to the first matching page and reset clears every filter", async ({ page }) => {
  await publicSearch(page);
  await page.goto("/en?q=Scala&sort=oldest&limit=10&offset=40");
  await expect(page.getByText("There are no posts on this page.")).toBeVisible();
  await page.getByRole("link", { name: "First matching page" }).click();
  await expect(page.getByRole("heading", { level: 3 })).toHaveText("Scala / 0");
  expect(new URL(page.url()).searchParams.get("sort")).toBe("oldest");
  await page.getByLabel("Search posts", { exact: true }).fill("missing");
  await page.getByLabel("Search posts", { exact: true }).press("Enter");
  await expect(page.getByText("No posts match your search.")).toBeVisible();
  await expect(page.getByRole("link", { name: "Next page" })).toHaveCount(0);
  await page.getByRole("link", { name: "Reset search" }).click();
  await expect(page).toHaveURL(/\/en$/);
  await expect(page.getByLabel("Search posts", { exact: true })).toHaveValue("");
  await expect(page.getByLabel("Sort by")).toHaveValue("newest");
  await expect(page.getByLabel("Posts per page")).toHaveValue("20");
});

test("encoded punctuation reaches the API once and the mobile controls fit the viewport", async ({ page }, testInfo) => {
  const requests = [];
  await publicSearch(page, requests);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/en");
  const text = "100%_! & + café";
  await page.getByLabel("Search posts", { exact: true }).fill(text);
  await page.getByLabel("Search posts", { exact: true }).press("Enter");
  await expect(page.getByRole("heading", { level: 3 })).toHaveText(text + " / 0");
  expect(requests.at(-1).q).toBe(text);
  await expect(page.getByRole("search")).toBeVisible();
  await expect(page.getByLabel("Search posts", { exact: true })).toHaveAttribute("maxlength", "100");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("search-mobile.png"), fullPage: true });
});

test("invalid search routes fail before requesting list data", async ({ page }) => {
  let fetched = 0;
  await page.route("**/service/blog/posts?**", route => { fetched++; return reply(route, { size: 0 }); });
  for (const query of ["status=DRAFT", "sort=content", "limit=101", "q=" + "x".repeat(101), "q=a%00b"]) {
    await page.goto("/en?" + query);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Invalid page");
  }
  expect(fetched).toBe(0);
});

test("editorial status filters and advertised page links preserve the search", async ({ page }) => {
  const requests = [];
  await page.route("**/service/auth/session", route => reply(route, {
    csrfToken: "search-test", account: { id: "admin", version: 0, email: "admin@example.test", role: "ADMIN" },
    $links: [link("editorial", "/service/editorial/posts")]
  }));
  await page.route("**/service/editorial/posts?**", route => {
    const query = new URL(route.request().url()).searchParams;
    requests.push(Object.fromEntries(query));
    const offset = Number(query.get("offset"));
    const next = new URLSearchParams(query);
    next.set("offset", "10");
    return reply(route, { size: 11, rows: [row("Editorial " + offset)], $links: [
      link("create", "/service/editorial/posts", "POST"),
      ...(offset === 0 ? [link("next", "/service/editorial/posts?" + next)] : [])
    ] });
  });
  await page.goto("/en/editorial?offset=20");
  await expect(page.getByLabel("Sort by")).toHaveValue("title");
  await page.getByLabel("Search posts", { exact: true }).fill("draft & notes");
  await page.getByLabel("Publication status", { exact: true }).selectOption("DRAFT");
  await page.getByLabel("Posts per page").selectOption("10");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await expect(page.getByRole("heading", { level: 2 })).toHaveText("Editorial 0");
  expect(requests.at(-1)).toMatchObject({ q: "draft & notes", status: "DRAFT", limit: "10", offset: "0" });
  await page.getByRole("link", { name: "Next page" }).click();
  await expect(page.getByRole("heading", { level: 2 })).toHaveText("Editorial 10");
  await expect(page.getByLabel("Publication status", { exact: true })).toHaveValue("DRAFT");
  expect(requests.at(-1).q).toBe("draft & notes");
  await expect(page.getByRole("link", { name: "New post", exact: true })).toBeVisible();
});

test("a delayed obsolete search cannot replace a newer navigation", async ({ page }) => {
  await publicSearch(page);
  let release;
  const pending = new Promise(resolve => release = resolve);
  let settled;
  const completed = new Promise(resolve => settled = resolve);
  await page.route("**/service/blog/posts?**q=slow**", async route => {
    await pending;
    await reply(route, { size: 1, rows: [row("Obsolete result")] }).catch(() => {});
    settled();
  });
  await page.goto("/en");
  await page.getByLabel("Search posts", { exact: true }).fill("slow");
  const start = page.waitForRequest(request => new URL(request.url()).searchParams.get("q") === "slow");
  await page.getByRole("button", { name: "Search", exact: true }).click();
  await start;
  await expect(page.getByRole("status")).toHaveText("Loading posts…");
  await page.getByRole("navigation", { name: "Main navigation" }).getByRole("link", { name: "Latest posts" }).click();
  await expect(page.getByRole("heading", { level: 3 })).toHaveText("All posts / 0");
  release();
  await completed;
  await expect(page.getByRole("heading", { level: 3 })).toHaveText("All posts / 0");
  await expect(page).toHaveURL(/\/en$/);
});
