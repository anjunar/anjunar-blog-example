import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";

function sql(statement) {
  const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
  execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
    "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER,
    "-d", url.pathname.slice(1), "-c", statement], {
    env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD }, stdio: ["ignore", "pipe", "pipe"]
  });
}

test("real searches paginate published summaries and filter editorial drafts", async ({ page }) => {
  const marker = "search-" + randomUUID();
  const ids = Array.from({ length: 4 }, () => randomUUID());
  try {
    for (let index = 0; index < ids.length; index++) {
      const title = marker + [" Alpha", " Beta", " Z 100%_!", " Draft"][index];
      sql(`insert into public.blog_post (id,version,slug,title,content,summary,status,published_at)
        values ('${ids[index]}',0,'search-${ids[index]}','${title}','Body excluded from lists.',
          'A small summary','${index === 3 ? "DRAFT" : "PUBLISHED"}',
          ${index === 3 ? "NULL" : "'2026-09-27T12:00:00Z'"})`);
    }
    await page.goto("/en?limit=1&sort=title&q=" + marker);
    await expect(page.getByRole("heading", { level: 3 })).toHaveText(marker + " Alpha");
    await expect(page.locator(".list-note")).toHaveText("Showing 1 of 3 posts");
    await page.getByRole("link", { name: "Next page" }).click();
    await expect(page.getByRole("heading", { level: 3 })).toHaveText(marker + " Beta");
    await page.reload();
    await expect(page.getByLabel("Search posts", { exact: true })).toHaveValue(marker);
    await expect(page.getByLabel("Posts per page")).toHaveValue("1");
    await page.getByRole("link", { name: "Next page" }).click();
    await expect(page.getByRole("heading", { level: 3 })).toHaveText(marker + " Z 100%_!");
    await expect(page.getByRole("link", { name: "Next page" })).toHaveCount(0);
    await page.getByLabel("Search posts", { exact: true }).fill(marker + " Z 100%");
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await expect(page.locator(".list-note")).toHaveText("Showing 1 of 1 posts");
    expect(new URL(page.url()).searchParams.has("offset")).toBe(false);

    const response = await page.request.get("/service/blog/posts?q=" + marker);
    const result = await response.json();
    expect(result.size).toBe(3);
    expect(result.rows.every(row => row.data.content === undefined && row.data.status === "PUBLISHED")).toBe(true);

    await page.goto("/en/account");
    await page.getByLabel("Email", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_EMAIL);
    await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await page.getByRole("link", { name: "Open editorial" }).click();
    await page.getByLabel("Search posts", { exact: true }).fill(marker);
    await page.getByLabel("Publication status", { exact: true }).selectOption("DRAFT");
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await expect(page.locator(".list-note")).toHaveText("Showing 1 of 1 posts");
    await expect(page.getByRole("heading", { level: 2 })).toHaveText(marker + " Draft");
    await page.reload();
    await expect(page.getByLabel("Publication status", { exact: true })).toHaveValue("DRAFT");
    const authenticated = await page.request.get("/service/blog/posts?q=" + marker);
    expect((await authenticated.json()).size).toBe(3);
    await page.getByRole("link", { name: marker + " Draft", exact: true }).click();
    await expect(page.locator(".post-content")).toHaveText("Body excluded from lists.");
    await expect(page.getByRole("link", { name: "Edit post", exact: true })).toBeVisible();
  } finally {
    sql("delete from public.blog_post where id in (" + ids.map(id => "'" + id + "'").join(",") + ")");
  }
});
