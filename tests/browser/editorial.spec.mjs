import { test, expect } from "@playwright/test";

const id = "8a1c1582-e841-4a27-a506-1a630337df48";
const api = `/service/editorial/posts/${id}`;
const account = { id: "admin", email: "admin@example.test", role: "ADMIN", version: 0 };
const link = (rel, url, method = "GET") => ({ rel, url, method, "@type": "BlogPost" });
const envelope = (published = false, links) => ({
  data: { id, version: published ? 1 : 0, slug: "editorial-example", title: "A draft worth sharing",
    summary: "Publication follows the permissions returned by the server.",
    content: "<script>window.editorialInjection = true</script>\nA plain-text draft.",
    status: published ? "PUBLISHED" : "DRAFT", ...(published ? { publishedAt: "2026-09-27T10:00:00Z" } : {}) },
  $links: links ?? [link("self", api), published ? link("retract", api + "/retract", "POST") : link("publish", api + "/publish", "POST"),
    ...(published ? [link("public", "/service/blog/posts/editorial-example")] : [])]
});
const reply = (route, body, status = 200) =>
  route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });

async function session(page, links = [link("editorial", "/service/editorial/posts")]) {
  await page.route("**/service/auth/session", route => reply(route, { csrfToken: "editorial-token", account, $links: links }));
}

test("entry follows session links and draft actions follow returned methods and URLs", async ({ page }, testInfo) => {
  await session(page);
  let published = false;
  let writes = 0;
  // Deliberately a different action URL: the client must use the supplied link.
  const custom = "/service/editorial/commands/publish-example";
  await page.route("**/service/editorial/**", route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === "POST") {
      expect(path).toBe(published ? api + "/retract" : custom);
      expect(request.headers()["x-csrf-token"]).toBe("editorial-token");
      expect(request.postDataJSON()).toEqual({});
      writes++;
      published = !published;
      return reply(route, envelope(published, published ? undefined :
        [link("self", api), link("publish", custom, "POST")]));
    }
    const post = envelope(published, published ? undefined : [link("self", api), link("publish", custom, "POST")]);
    return reply(route, path.endsWith(id) ? post : { rows: [post], size: 1 });
  });
  await page.goto("/en/account");
  await page.getByRole("link", { name: "Open editorial" }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Editorial");
  await page.getByRole("link", { name: "A draft worth sharing" }).click();
  await expect(page.getByRole("button", { name: "Publish", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Retract" })).toHaveCount(0);
  expect(await page.evaluate(() => window.editorialInjection)).toBeUndefined();
  await page.screenshot({ path: testInfo.outputPath("editorial-desktop.png"), fullPage: true });
  await page.getByRole("button", { name: "Publish", exact: true }).click();
  await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Published");
  await expect(page.getByRole("button", { name: "Publish", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Open public post" })).toBeVisible();
  await page.getByRole("button", { name: "Retract" }).click();
  await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Draft");
  expect(writes).toBe(2);
});

test("an ADMIN label without links provides neither entry nor actions", async ({ page }) => {
  await session(page, []);
  await page.goto("/en/account");
  await expect(page.getByText("Administrator", { exact: true })).toBeVisible();
  await expect(page.getByRole("link", { name: "Open editorial" })).toHaveCount(0);
  await page.route("**/service/editorial/**", route => reply(route, envelope(false, [link("self", api)])));
  await page.goto(`/en/editorial/posts/${id}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("A draft worth sharing");
  await expect(page.getByRole("button", { name: /Publish|Retract/ })).toHaveCount(0);
});

for (const status of [401, 403]) test(`direct editorial navigation reports ${status}`, async ({ page }) => {
  await page.route("**/service/editorial/**", route => reply(route, {}, status));
  await page.goto(`/en/editorial/posts/${id}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(status === 401 ? "Sign in required" : "Access denied");
  await expect(page.getByRole("link", { name: "Your account", exact: true })).toBeVisible();
});

test("stale actions disappear after conflict and reload fetches the current state", async ({ page }, testInfo) => {
  await session(page);
  let stale = false;
  await page.route("**/service/editorial/**", route => {
    if (route.request().method() === "POST") { stale = true; return reply(route, {}, 409); }
    return reply(route, envelope(stale));
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`/en/editorial/posts/${id}`);
  await page.getByRole("button", { name: "Publish", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("The post changed. Reload before trying again.");
  await expect(page.getByRole("button", { name: "Publish", exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("editorial-mobile.png"), fullPage: true });
  await page.getByRole("button", { name: "Reload post" }).click();
  await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Published");
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("external action links never receive a request or a CSRF token", async ({ page }) => {
  await session(page);
  let external = 0;
  await page.route("https://untrusted.example/**", route => { external++; return reply(route, {}); });
  await page.route("**/service/editorial/**", route =>
    reply(route, envelope(false, [link("publish", "https://untrusted.example/service/publish", "POST")])));
  await page.goto(`/en/editorial/posts/${id}`);
  await page.getByRole("button", { name: "Publish", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("could not be confirmed");
  expect(external).toBe(0);
});

test("pending commands disable duplicates and ignore replies after leaving the page", async ({ page }) => {
  await session(page);
  let release;
  const gate = new Promise(resolve => release = resolve);
  let writes = 0;
  await page.route("**/service/editorial/**", async route => {
    if (route.request().method() === "POST") { writes++; await gate; return reply(route, envelope(true)); }
    return reply(route, envelope());
  });
  await page.goto(`/en/editorial/posts/${id}`);
  const started = page.waitForRequest(request => request.method() === "POST");
  await page.getByRole("button", { name: "Publish", exact: true }).click();
  await started;
  await expect(page.getByRole("button", { name: "Publish", exact: true })).toBeDisabled();
  await page.getByRole("link", { name: "Account", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Your account");
  release();
  expect(writes).toBe(1);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Your account");
});

test("editorial pagination preserves the offset and limit supplied by the server", async ({ page }) => {
  await session(page);
  const requests = [];
  await page.route("**/service/editorial/posts?**", route => {
    const url = new URL(route.request().url());
    requests.push([url.searchParams.get("offset"), url.searchParams.get("limit")]);
    const second = url.searchParams.get("offset") === "1";
    const row = envelope();
    row.data.title = second ? "Second editorial post" : "First editorial post";
    return reply(route, { rows: [row], size: 2, $links: second ? [] :
      [link("next", "/service/editorial/posts?offset=1&limit=1")] });
  });
  await page.goto("/en/editorial?limit=1");
  await expect(page.getByRole("link", { name: "First editorial post" })).toBeVisible();
  await page.getByRole("link", { name: "Next page" }).click();
  await expect(page.getByRole("link", { name: "Second editorial post" })).toBeVisible();
  expect(requests).toEqual([["0", "1"], ["1", "1"]]);
  await expect(page).toHaveURL(/offset=1&limit=1/);
});

test("an empty draft remains previewable when the mapper omits its empty content", async ({ page }) => {
  const draft = envelope(false, [link("self", api), link("update", api, "PATCH")]);
  delete draft.data.content;
  await page.route("**/service/editorial/**", route => reply(route, draft));
  await page.goto(`/en/editorial/posts/${id}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("A draft worth sharing");
  await expect(page.getByRole("status", { name: "Publication status" })).toHaveText("Draft");
  await expect(page.getByRole("button", { name: "Publish", exact: true })).toHaveCount(0);
  await expect(page.getByRole("alert")).toHaveCount(0);
});
