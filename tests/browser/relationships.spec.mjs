import { test, expect } from "@playwright/test";

const id = "8a1c1582-e841-4a27-a506-1a630337df48";
const link = (rel, url, method = "GET") => ({ rel, url, method });
const author = (id, displayName) => ({ id, version: 0, displayName });
const tag = (id, name) => ({ id, version: 0, slug: id, name });
const authors = [author("one", "Ada"), author("two", "Grace"), author("three", "Linus")];
const tags = [tag("scala", "Scala"), tag("jakarta", "Jakarta"), tag("database", "Database")];
const row = (data, kind) => ({ data, $links: [link("update", `/service/editorial/${kind}/${data.id}`, "PATCH")] });
const post = () => ({ id, version: 0, slug: "relationships", title: "Relationships", content: "A complete post",
  status: "DRAFT", author: authors[0], tags: [tags[0]] });
const detail = data => ({ data, $links: [link("update", `/service/editorial/posts/${id}`, "PATCH")] });
const reply = (route, data, status = 200) =>
  route.fulfill({ status, contentType: "application/json", body: JSON.stringify(data) });
const edit = `/en/editorial/posts/${id}/edit`;

async function catalogs(page, { paged = false } = {}) {
  await page.route("**/service/auth/session", route => reply(route, {
    csrfToken: "references-token", account: { ...authors[0], role: "ADMIN" },
    $links: [link("editorial", "/service/editorial/posts"), link("authors", "/service/editorial/authors"), link("tags", "/service/editorial/tags")]
  }));
  await page.route("**/service/editorial/authors", route => reply(route, {
    rows: authors.map(value => row(value, "authors")), size: authors.length
  }));
  await page.route("**/service/editorial/tags", route => reply(route, {
    rows: (paged ? tags.slice(0, 1) : tags).map(value => row(value, "tags")), size: tags.length,
    $links: [link("create", "/service/editorial/tags", "POST"),
      ...(paged ? [link("next", "/service/editorial/tags?offset=1&limit=2")] : [])]
  }));
  if (paged) await page.route("**/service/editorial/tags?offset=1&limit=2", route => reply(route, {
    rows: tags.slice(1).map(value => row(value, "tags")), size: tags.length
  }));
}

async function choose(page, field, text) {
  const control = page.getByRole("combobox", { name: field, exact: true });
  await control.click();
  await page.locator(".ui-combo-box__item").filter({ hasText: new RegExp(`^${text}$`) }).click();
  if (field === "Tags") await control.press("Escape");
}

function apply(value, body) {
  return { ...value, ...body, version: value.version + 1,
    author: Object.hasOwn(body, "author") ? authors.find(item => item.id === body.author?.id) : value.author,
    tags: Object.hasOwn(body, "tags") ? body.tags.map(ref => tags.find(item => item.id === ref.id)) : value.tags };
}

test("relationship controls send ID-only references, preserve version and clear selections", async ({ page }, info) => {
  await catalogs(page);
  let value = post();
  const writes = [];
  await page.route(`**/service/editorial/posts/${id}`, route => {
    if (route.request().method() === "PATCH") {
      const body = route.request().postDataJSON();
      writes.push(body);
      expect(route.request().headers()["x-csrf-token"]).toBe("references-token");
      value = apply(value, body);
    }
    return reply(route, detail(value));
  });
  await page.goto(edit);
  await expect(page.getByRole("combobox", { name: "Author", exact: true })).toContainText("Ada");
  await expect(page.getByRole("combobox", { name: "Tags", exact: true })).toContainText("Scala");
  await choose(page, "Author", "Grace");
  await choose(page, "Tags", "Jakarta");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(writes[0].author).toEqual({ id: "two" });
  expect(writes[0].tags).toEqual([{ id: "scala" }, { id: "jakarta" }]);
  expect(writes[0].version).toBe(0);
  await page.getByRole("button", { name: "Clear author", exact: true }).click();
  await page.getByRole("button", { name: "Clear tags", exact: true }).click();
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(writes[1]).toMatchObject({ version: 1, author: null, tags: [] });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: info.outputPath("relationships-mobile.png"), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test("a delayed save keeps newer relationship selections", async ({ page }) => {
  await catalogs(page);
  let value = post();
  let release;
  const gate = new Promise(resolve => release = resolve);
  await page.route(`**/service/editorial/posts/${id}`, async route => {
    if (route.request().method() === "PATCH") {
      value = apply(value, route.request().postDataJSON());
      await gate;
    }
    return reply(route, detail(value));
  });
  await page.goto(edit);
  await choose(page, "Author", "Grace");
  const started = page.waitForRequest(request => request.method() === "PATCH");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await started;
  await choose(page, "Author", "Linus");
  await choose(page, "Tags", "Database");
  release();
  await expect(page.getByRole("status")).toHaveText("Saved. Your newer edits still need saving.");
  await expect(page.getByRole("combobox", { name: "Author", exact: true })).toContainText("Linus");
  await expect(page.getByRole("combobox", { name: "Tags", exact: true })).toContainText("Database");
});

test("catalog pagination loads more choices without losing selected tags", async ({ page }) => {
  await catalogs(page, { paged: true });
  await page.route(`**/service/editorial/posts/${id}`, route => reply(route, detail(post())));
  await page.goto(edit);
  await page.getByRole("button", { name: "Load more tags", exact: true }).click();
  await expect(page.getByRole("button", { name: "Load more tags", exact: true })).toHaveCount(0);
  await choose(page, "Tags", "Database");
  await expect(page.getByRole("combobox", { name: "Tags", exact: true })).toContainText("Scala, Database");
});

test("catalog forms bind public names and tag fields, then retain returned versions", async ({ page }, info) => {
  await catalogs(page);
  const writes = [];
  await page.route("**/service/editorial/authors/one", route => {
    const body = route.request().postDataJSON();
    writes.push(body);
    return reply(route, row({ ...authors[0], ...body, version: body.version + 1 }, "authors"));
  });
  await page.route("**/service/editorial/tags", route => {
    if (route.request().method() === "POST") {
      const body = route.request().postDataJSON();
      writes.push(body);
      return reply(route, row({ id: "new-tag", version: 0, ...body }, "tags"), 201);
    }
    return reply(route, { rows: [], size: 0, $links: [link("create", "/service/editorial/tags", "POST")] });
  });
  await page.goto("/en/editorial/relationships");
  await page.locator("#author-name-one").fill("Ada Lovelace");
  await page.getByRole("form", { name: "Author details", exact: true }).first()
    .getByRole("button", { name: "Save author", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Changes saved.");
  expect(writes[0]).toMatchObject({ version: 0, displayName: "Ada Lovelace" });
  expect(writes[0]).not.toHaveProperty("email");
  await page.getByLabel("New tag name", { exact: true }).fill("New category");
  await page.getByLabel("New tag slug", { exact: true }).fill("new-category");
  await page.getByRole("button", { name: "Create tag", exact: true }).click();
  await expect(page.getByRole("heading", { name: "New category", exact: true })).toBeVisible();
  expect(writes[1]).toMatchObject({ name: "New category", slug: "new-category" });
  expect(writes[1]).not.toHaveProperty("id");
  await expect(page.getByLabel("New tag name", { exact: true })).toHaveValue("");
  await page.screenshot({ path: info.outputPath("relationship-catalog.png"), fullPage: true });
});
