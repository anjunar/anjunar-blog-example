import { test, expect } from "@playwright/test";

// This project uses a migrated test database seeded with database/examples/public-posts.sql.
// It does not intercept requests or create/delete database rows.
test("a PostgreSQL post travels through REST, JSON mapping, list and detail", async ({ page, request }, testInfo) => {
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  const api = await request.get("/service/blog/posts/our-first-public-post");
  expect(api.status()).toBe(200);
  const { data } = await api.json();
  expect(data.version).toBe(0);
  await page.goto("/");
  await expect(page.getByRole("link", { name: data.title, exact: true })).toBeVisible();
  await expect(page.getByText("Our private draft", { exact: true })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath("database-list.png"), fullPage: true });
  await page.getByRole("link", { name: data.title, exact: true }).click();
  await expect(page).toHaveURL(/\/en\/posts\/our-first-public-post$/);
  await expect(page.locator(".post-content")).toHaveText(data.content);
  await expect(page.locator(".detail-summary")).toHaveText(data.summary);
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(data.title);
  await page.screenshot({ path: testInfo.outputPath("database-detail.png"), fullPage: true });
  expect(errors).toEqual([]);
});

test("draft and unknown slugs return the same API and UI result", async ({ page, request }) => {
  for (const slug of ["our-private-draft", "no-such-public-post"]) {
    expect((await request.get("/service/blog/posts/" + slug)).status()).toBe(404);
    const document = await page.goto("/en/posts/" + slug);
    // The static shell is 200 until the SSR chapter. The data request is 404.
    expect(document.status()).toBe(200);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Post not found");
    await expect(page.locator(".post-content")).toHaveCount(0);
  }
});
