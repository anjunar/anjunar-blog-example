import { test, expect } from "@playwright/test";

const id = "8a1c1582-e841-4a27-a506-1a630337df48";
const api = `/service/editorial/posts/${id}`;
const edit = `/en/editorial/posts/${id}/edit`;
const link = (rel, url, method = "GET") => ({ rel, url, method });
const data = () => ({ id, version: 0, slug: "first-post", title: "First title",
  content: "Original content", summary: "Original summary", status: "DRAFT" });
const envelope = (post = data(), links = [link("update", api, "PATCH")]) => ({
  data: Object.fromEntries(Object.entries(post).filter(([, value]) => value !== null && value !== "")), $links: links
});
const reply = (route, body, status = 200, contentType = "application/json") =>
  route.fulfill({ status, contentType, body: JSON.stringify(body) });
const problem = (route, status, errors = []) => reply(route, {
  type: status === 409 ? "/problems/conflict" : "/problems/validation",
  title: "Request rejected", status, detail: "Cannot save these values.", errors
}, status, "application/problem+json");

async function session(page) {
  await page.route("**/service/auth/session", route => reply(route, {
    csrfToken: "form-token", account: { id: "admin", email: "admin@example.test", role: "ADMIN" },
    $links: [link("editorial", "/service/editorial/posts")]
  }));
}

test("post form follows the update link, sends clean version zero and clears optional text", async ({ page }, info) => {
  await session(page);
  const custom = "/service/editorial/commands/save";
  let post = data();
  const writes = [];
  await page.route("**/service/editorial/**", route => {
    if (route.request().method() === "PATCH") {
      expect(new URL(route.request().url()).pathname).toBe(custom);
      expect(route.request().headers()["x-csrf-token"]).toBe("form-token");
      const body = route.request().postDataJSON();
      writes.push(body);
      post = { ...post, ...body, version: post.version + 1 };
    }
    return reply(route, envelope(post, [link("update", custom, "PATCH")]));
  });
  await page.goto(edit);
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("First title");
  await page.getByLabel("Title", { exact: true }).fill("Edited title");
  await page.getByLabel("Summary (optional)").fill("");
  await page.getByLabel("Content", { exact: true }).fill("");
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(writes[0]).toMatchObject({ version: 0, title: "Edited title", summary: null, content: "" });
  expect(writes[0]).not.toHaveProperty("slug");
  expect(writes[0]).not.toHaveProperty("status");
  expect(writes[0]).not.toHaveProperty("publishedAt");
  await page.getByLabel("Title", { exact: true }).fill("Next title");
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(writes[1].version).toBe(1);
  expect(writes[1]).not.toHaveProperty("summary");
  await page.screenshot({ path: info.outputPath("post-editor-desktop.png"), fullPage: true });
  await page.reload();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Next title");
});

test("new post form follows create and opens the saved post's edit route", async ({ page }) => {
  await session(page);
  let post;
  let writes = 0;
  let updates = 0;
  let release;
  const gate = new Promise(resolve => release = resolve);
  await page.route("**/service/editorial/**", async route => {
    if (route.request().method() === "POST") {
      expect(new URL(route.request().url()).pathname).toBe("/service/editorial/create-draft");
      const body = route.request().postDataJSON();
      expect(body).not.toHaveProperty("id");
      expect(body).not.toHaveProperty("status");
      post = { ...data(), ...body, summary: null, content: "", version: 0 };
      writes++;
      await gate;
      return reply(route, envelope(post), 201);
    }
    if (route.request().method() === "PATCH") {
      post = { ...post, ...route.request().postDataJSON(), version: 1 };
      updates++;
    }
    if (new URL(route.request().url()).pathname === api) return reply(route, envelope(post));
    return reply(route, { size: 0, $links: [link("create", "/service/editorial/create-draft", "POST")] });
  });
  await page.goto("/en/editorial");
  await page.getByRole("link", { name: "New post", exact: true }).click();
  await page.getByLabel("Title", { exact: true }).fill("New draft title");
  await page.getByLabel("Slug", { exact: true }).fill("new-draft-title");
  const started = page.waitForRequest(request => request.method() === "POST");
  await page.getByRole("button", { name: "Save post" }).click();
  await started;
  await page.getByLabel("Title", { exact: true }).fill("Newer text during creation");
  release();
  await expect(page.getByRole("status")).toHaveText("Saved. Your newer edits still need saving.");
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Newer text during creation");
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page).toHaveURL(edit);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Edit post");
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Newer text during creation");
  expect(writes).toBe(1);
  expect(updates).toBe(1);
});

test("form validation binds entity constraints and prevents invalid requests", async ({ page }, info) => {
  await session(page);
  let writes = 0;
  await page.route("**/service/editorial/**", route => {
    if (route.request().method() !== "GET") writes++;
    return reply(route, envelope());
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(edit);
  await page.getByLabel("Title", { exact: true }).fill("x");
  await page.getByLabel("Slug", { exact: true }).fill("Wrong Slug");
  await page.getByLabel("Summary (optional)").fill("x".repeat(301));
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByRole("alert")).toContainText("Review the fields");
  for (const label of ["Title", "Slug", "Summary (optional)"])
    await expect(page.getByLabel(label, { exact: true })).toHaveAttribute("aria-invalid", "true");
  expect(writes).toBe(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath("post-editor-mobile.png"), fullPage: true });
});

test("server field errors attach to controls and cross-field errors remain visible", async ({ page }) => {
  await session(page);
  await page.route("**/service/editorial/**", route => route.request().method() === "PATCH"
    ? problem(route, 400, [{ path: ["title"], message: "A server-side title rule failed." },
      { path: ["publicationConsistent"], message: "A published post needs content." }])
    : reply(route, envelope()));
  await page.goto(edit);
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByText("A server-side title rule failed.", { exact: true })).toBeVisible();
  await expect(page.getByRole("alert")).toContainText("A published post needs content.");
  await expect(page.getByLabel("Title", { exact: true })).toHaveAttribute("aria-invalid", "true");
  await expect(page.getByRole("button", { name: "Save post" })).toBeEnabled();
});

test("a slug conflict can be corrected without discarding the draft", async ({ page }) => {
  await session(page);
  let writes = 0;
  await page.route("**/service/editorial/**", route => {
    if (route.request().method() === "PATCH" && ++writes === 1)
      return problem(route, 409, [{ path: ["slug"], message: "Choose an unused slug." }]);
    return reply(route, envelope());
  });
  await page.goto(edit);
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByText("Choose an unused slug.", { exact: true })).toBeVisible();
  await page.getByLabel("Slug", { exact: true }).fill("another-slug");
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(writes).toBe(2);
});

test("a stale version preserves local edits until an explicit discard and reload", async ({ page }) => {
  await session(page);
  let stale = false;
  await page.route("**/service/editorial/**", route => {
    if (route.request().method() === "PATCH") { stale = true; return problem(route, 409); }
    return reply(route, envelope(stale ? { ...data(), title: "Another editor saved", version: 1 } : data()));
  });
  await page.goto(edit);
  await page.getByLabel("Title", { exact: true }).fill("Keep my local title");
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByRole("alert")).toContainText("changed elsewhere");
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Keep my local title");
  await expect(page.getByRole("button", { name: "Save post" })).toBeDisabled();
  await page.getByRole("button", { name: "Discard my edits and reload" }).click();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Another editor saved");
  await expect(page.getByRole("button", { name: "Save post" })).toBeEnabled();
});

test("slow saves freeze the payload, prevent duplicates and preserve newer typing", async ({ page }) => {
  await session(page);
  let release;
  const gate = new Promise(resolve => release = resolve);
  const writes = [];
  await page.route("**/service/editorial/**", async route => {
    if (route.request().method() === "GET") return reply(route, envelope());
    const body = route.request().postDataJSON();
    writes.push(body);
    if (writes.length === 1) await gate;
    return reply(route, envelope({ ...data(), ...body, version: writes.length }));
  });
  await page.goto(edit);
  await page.getByLabel("Title", { exact: true }).fill("Submitted title");
  const started = page.waitForRequest(request => request.method() === "PATCH");
  await page.getByRole("button", { name: "Save post" }).click();
  await started;
  await expect(page.getByRole("button", { name: "Save post" })).toBeDisabled();
  await page.getByLabel("Title", { exact: true }).fill("Still typing");
  release();
  await expect(page.getByRole("status")).toHaveText("Saved. Your newer edits still need saving.");
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Still typing");
  expect(writes[0].title).toBe("Submitted title");
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(writes[1]).toMatchObject({ title: "Still typing", version: 1 });
});

test("a late field rejection does not mark a corrected value invalid", async ({ page }) => {
  await session(page);
  let release;
  const gate = new Promise(resolve => release = resolve);
  await page.route("**/service/editorial/**", async route => {
    if (route.request().method() === "GET") return reply(route, envelope());
    await gate;
    return problem(route, 400, [{ path: ["title"], message: "Old value rejected." }]);
  });
  await page.goto(edit);
  const started = page.waitForRequest(request => request.method() === "PATCH");
  await page.getByRole("button", { name: "Save post" }).click();
  await started;
  await page.getByLabel("Title", { exact: true }).fill("Corrected while saving");
  release();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByLabel("Title", { exact: true })).toHaveAttribute("aria-invalid", "false");
  await expect(page.getByText("Old value rejected.", { exact: true })).toHaveCount(0);
});

for (const status of [401, 403, 500]) test(`failed save ${status} preserves input and stops blind retries`, async ({ page }) => {
  await session(page);
  await page.route("**/service/editorial/**", route => route.request().method() === "GET"
    ? reply(route, envelope()) : problem(route, status));
  await page.goto(edit);
  await page.getByLabel("Title", { exact: true }).fill("Keep this text");
  await page.getByRole("button", { name: "Save post" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Keep this text");
  await expect(page.getByRole("button", { name: "Save post" })).toBeDisabled();
});

test("missing capabilities and external update destinations do not open a writable form", async ({ page }) => {
  let external = 0;
  await page.route("https://untrusted.example/**", route => { external++; return reply(route, {}); });
  await page.route("**/service/editorial/**", route => reply(route, envelope(data(), [])));
  await page.goto(edit);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Access denied");
  await page.unroute("**/service/editorial/**");
  await page.route("**/service/editorial/**", route => reply(route, envelope(data(),
    [link("update", "https://untrusted.example/service/save", "PATCH")])));
  await page.goto(edit);
  await expect(page.getByRole("button", { name: "Save post" })).toHaveCount(0);
  expect(external).toBe(0);
});

test("leaving a pending form prevents a delayed response from changing the new page", async ({ page }) => {
  await session(page);
  let release;
  const gate = new Promise(resolve => release = resolve);
  await page.route("**/service/editorial/**", async route => {
    if (route.request().method() === "PATCH") await gate;
    return reply(route, envelope());
  });
  await page.goto(edit);
  const started = page.waitForRequest(request => request.method() === "PATCH");
  await page.getByRole("button", { name: "Save post" }).click();
  await started;
  await page.getByRole("link", { name: "Account", exact: true }).click();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Your account");
  release();
  await expect(page.getByRole("button", { name: "Save post" })).toHaveCount(0);
});
