import { test, expect } from "@playwright/test";

const posts = [
  { id: "first-id", version: 0, slug: "first-post", title: "A working REST contract",
    summary: "From PostgreSQL to a browser model.", status: "PUBLISHED",
    publishedAt: "2026-09-27T10:15:42.123456Z" },
  { id: "second-id", version: 3, slug: "second-post", title: "Reading a complete post",
    status: "PUBLISHED", publishedAt: "2026-09-26T09:00:00Z" },
];
const content = 'Plain text stays plain.\n<script>window.injected = true</script>';
const row = post => ({ "@type": "Data", data: { "@type": "BlogPost", ...post },
  schema: { entries: [{ name: "title", type: "String" }] } });
const table = (values = posts, size = values.length) => ({
  "@type": "Table", size, ...(values.length ? { rows: values.map(row) } : {}),
});
const json = (route, value, status = 200) =>
  route.fulfill({ status, contentType: "application/json", body: JSON.stringify(value) });

async function mockApi(page) {
  await page.route("**/service/blog/posts**", route => {
    const url = new URL(route.request().url());
    if (url.pathname === "/service/blog/posts") return json(route, table());
    const post = posts.find(post => url.pathname.endsWith("/" + post.slug));
    return post ? json(route, row({ ...post, content })) : json(route, {}, 404);
  });
}

let runtimeErrors;
test.beforeEach(({ page }) => {
  runtimeErrors = [];
  page.on("pageerror", error => runtimeErrors.push(error.message));
});
test.afterEach(() => expect(runtimeErrors).toEqual([]));

test("REST rows render with optional summaries and accessible mobile controls", async ({ page }, testInfo) => {
  await mockApi(page);
  await page.goto("/");
  await expect(page).toHaveTitle("Anjunar Journal");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByRole("heading", { level: 3 })).toHaveText(posts.map(post => post.title));
  await expect(page.locator(".list-note")).toHaveText("Showing 2 of 2 posts");
  await expect(page.locator(".post-summary")).toHaveCount(1);
  await expect(page.locator(".post-date")).toHaveText(["2026-09-27", "2026-09-26"]);
  await expect(page.getByRole("link", { name: posts[0].title })).toHaveAttribute("href", "/en/posts/first-post");
  await page.screenshot({ path: testInfo.outputPath("desktop.png"), fullPage: true });

  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const summaries = page.getByRole("button", { name: "Show summaries" });
  expect((await summaries.boundingBox()).height).toBeGreaterThanOrEqual(44);
  await summaries.focus();
  await page.keyboard.press("Space");
  await expect(summaries).toBeFocused();
  await expect(summaries).toHaveAttribute("aria-pressed", "false");
  await expect(page.locator(".post-summary")).toHaveCount(0);
  await page.getByRole("link", { name: "Skip to content" }).focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("main")).toBeFocused();
  await page.screenshot({ path: testInfo.outputPath("mobile.png"), fullPage: true });
});

test("detail navigation, history and direct reload load real route models", async ({ page }, testInfo) => {
  await mockApi(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Show summaries" }).click();
  await page.getByRole("link", { name: posts[0].title }).click();
  await expect(page).toHaveURL(/\/en\/posts\/first-post$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(posts[0].title);
  await expect(page.locator(".post-content")).toHaveText(content);
  expect(await page.evaluate(() => window.injected)).toBeUndefined();
  await expect(page.locator(".post-content script")).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath("detail.png"), fullPage: true });
  await page.goBack();
  await expect(page.getByRole("article")).toHaveCount(2);
  await expect(page.getByRole("button", { name: "Show summaries" })).toHaveAttribute("aria-pressed", "false");
  await page.goForward();
  await expect(page.locator(".post-content")).toHaveText(content);
  const response = await page.reload();
  expect(response.status()).toBe(200);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(posts[0].title);
});

test("a pending request announces loading until its rows arrive", async ({ page }) => {
  let release;
  const pending = new Promise(resolve => release = resolve);
  await page.route("**/service/blog/posts?**", async route => {
    await pending;
    await json(route, table());
  });
  await page.goto("/");
  await expect(page.getByRole("status")).toHaveText("Loading posts…");
  await expect(page.getByRole("article")).toHaveCount(0);
  release();
  await expect(page.getByRole("article")).toHaveCount(2);
  await expect(page.getByRole("status")).toHaveCount(0);
});

test("omitted rows represent an empty blog or an empty page", async ({ page }) => {
  await page.route("**/service/blog/posts?**", route =>
    json(route, table([], new URL(route.request().url()).searchParams.get("offset") === "0" ? 0 : 23)));
  await page.goto("/");
  await expect(page.getByText("No posts have been published yet.")).toBeVisible();
  await expect(page.getByRole("article")).toHaveCount(0);
  await page.goto("/en?offset=40");
  await expect(page.getByText("There are no posts on this page.")).toBeVisible();
  await expect(page.locator(".list-note")).toHaveText("Showing 0 of 23 posts");
  await page.getByRole("link", { name: "Back to latest posts" }).click();
  await expect(page.getByText("No posts have been published yet.")).toBeVisible();
});

test("paging keeps server order and query state in browser history", async ({ page }) => {
  const requests = [];
  await page.route("**/service/blog/posts?**", route => {
    const query = new URL(route.request().url()).searchParams;
    requests.push([query.get("offset"), query.get("limit")]);
    return json(route, table(query.get("offset") === "0" ? posts : [posts[1]], 21));
  });
  await page.goto("/");
  await page.getByRole("link", { name: "Next page" }).click();
  await expect(page).toHaveURL(/\/en\?offset=20$/);
  await expect(page.getByRole("heading", { level: 3 })).toHaveText([posts[1].title]);
  await expect(page.getByRole("link", { name: "Next page" })).toHaveCount(0);
  await page.goBack();
  await expect(page.getByRole("heading", { level: 3 })).toHaveText(posts.map(post => post.title));
  expect(requests).toEqual([["0", "20"], ["20", "20"], ["0", "20"]]);
});

test("HTTP failure offers a retry at the same address", async ({ page }) => {
  let attempts = 0;
  await page.route("**/service/blog/posts?**", route =>
    ++attempts === 1
      ? route.fulfill({ status: 500, contentType: "text/html", body: "Private server details" })
      : json(route, table()));
  await page.goto("/en?offset=20");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Posts are unavailable");
  await expect(page.getByText("Private server details")).toHaveCount(0);
  await page.getByRole("button", { name: "Try again" }).click();
  await expect(page.getByRole("article")).toHaveCount(2);
  await expect(page).toHaveURL(/\/en\?offset=20$/);
  expect(attempts).toBe(2);
});

test("malformed JSON and network failures render the error boundary", async ({ page }) => {
  await page.route("**/service/blog/posts?**", route =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{broken" }));
  await page.goto("/");
  await expect(page.getByRole("alert")).toContainText("Posts are unavailable");
  await page.unroute("**/service/blog/posts?**");
  await page.route("**/service/blog/posts?**", route => route.abort("failed"));
  await page.reload();
  await expect(page.getByRole("alert")).toContainText("Posts are unavailable");
});

test("unknown posts keep their URL and invalid offsets do not fetch", async ({ page }) => {
  await mockApi(page);
  await page.goto("/en/posts/missing-post");
  await expect(page.getByRole("alert")).toContainText("Post not found");
  await expect(page).toHaveURL(/\/en\/posts\/missing-post$/);
  await expect(page.getByRole("button", { name: "Try again" })).toHaveCount(0);
  let requests = 0;
  page.on("request", request => { if (request.url().includes("/service/blog/")) requests++; });
  for (const offset of ["-1", "abc", "2147483648"]) {
    await page.goto("/en?offset=" + offset);
    await expect(page.getByRole("alert")).toContainText("Invalid page");
  }
  expect(requests).toBe(0);
});

test("leaving a slow detail aborts its request and prevents a stale page", async ({ page }) => {
  await mockApi(page);
  let release;
  const pending = new Promise(resolve => release = resolve);
  let completed;
  const settled = new Promise(resolve => completed = resolve);
  await page.route("**/service/blog/posts/first-post", async route => {
    await pending;
    await json(route, row({ ...posts[0], content }));
    completed();
  });
  await page.goto("/");
  const started = page.waitForRequest("**/service/blog/posts/first-post");
  await page.getByRole("link", { name: posts[0].title }).click();
  await started;
  await expect(page.getByRole("status")).toBeVisible();
  const aborted = page.waitForEvent("requestfailed", request => request.url().endsWith("/first-post"));
  await page.getByRole("navigation", { name: "Main navigation" }).getByRole("link", { name: "Latest posts" }).click();
  await aborted;
  await expect(page.getByRole("article")).toHaveCount(2);
  release();
  await settled;
  await expect(page).toHaveURL(/\/en$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("From idea to working software.");
});

test("known page routes serve HTML while API and unknown assets keep their status", async ({ request }) => {
  for (const path of ["/", "/en", "/en/", "/en/posts/first-post", "/en/posts/missing-post"]) {
    const response = await request.get(path);
    expect(response.status(), path).toBe(200);
    expect(response.headers()["content-type"]).toContain("text/html");
    expect(response.headers()["cache-control"]).toBe("no-cache");
    expect(await response.text()).toContain('type="module" src="/main.js"');
    const head = await request.head(path);
    expect(head.status()).toBe(200);
    expect(await head.body()).toHaveLength(0);
    expect((await request.post(path)).status()).toBe(405);
  }
  expect((await request.get("/main.js")).headers()["content-type"]).toMatch(/javascript/);
  expect((await request.get("/style.css")).headers()["content-type"]).toContain("text/css");
  expect(await (await request.get("/service/health/live")).text()).toBe("UP\n");
  for (const path of ["/service/no-such-resource", "/service-other", "/no-such-page", "/.env",
    "/build.sbt", "/en/main.js", "/en/posts/first-post/extra"]) {
    expect((await request.get(path)).status(), path).toBe(404);
  }
});

test("the index document alias preserves the query and opens the list", async ({ page, request }) => {
  const redirect = await request.get("/index.html?offset=20", { maxRedirects: 0 });
  expect(redirect.status()).toBe(307);
  expect(redirect.headers().location).toBe("/?offset=20");
  await mockApi(page);
  await page.goto("/index.html?offset=20");
  await expect(page).toHaveURL(/\/\?offset=20$/);
  await expect(page.getByRole("heading", { level: 3 })).toHaveCount(2);
});
