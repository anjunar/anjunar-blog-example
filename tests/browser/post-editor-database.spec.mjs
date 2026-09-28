import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";

test("an administrator creates, edits and resolves a stale form against PostgreSQL", async ({ page }) => {
  expect(process.env.BLOG_TEST_ADMIN_EMAIL).toBeTruthy();
  expect(process.env.BLOG_TEST_ADMIN_PASSWORD).toBeTruthy();
  await page.goto("/en/account");
  await page.getByLabel("Email", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_EMAIL);
  await page.getByLabel("Password", { exact: true }).fill(process.env.BLOG_TEST_ADMIN_PASSWORD);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByRole("link", { name: "Open editorial" }).click();
  await page.getByRole("link", { name: "New post", exact: true }).click();
  const slug = "form-" + crypto.randomUUID();
  await page.getByLabel("Title", { exact: true }).fill("A post written in the form");
  await page.getByLabel("Slug", { exact: true }).fill(slug);
  await page.getByLabel("Summary (optional)").fill("An editable summary");
  await page.getByRole("textbox", { name: "content", exact: true }).pressSequentially("Content bound directly to the post.");

  let id;
  try {
    const creation = page.waitForResponse(response =>
      response.request().method() === "POST" && new URL(response.url()).pathname === "/service/editorial/posts");
    await page.getByRole("button", { name: "Save post" }).click();
    const created = await creation;
    expect(created.status()).toBe(201);
    const result = await created.json();
    id = result.data.id;
    expect(result.data.version).toBe(0);
    await expect(page).toHaveURL(new RegExp("/en/editorial/posts/" + id + "/edit$"));
    await expect(page.getByRole("textbox", { name: "content", exact: true })).toHaveText("Content bound directly to the post.");
    await page.getByLabel("Title", { exact: true }).fill("Saved through a bound form");
    await page.getByLabel("Summary (optional)").fill("");
    const update = page.waitForResponse(response => response.request().method() === "PATCH");
    await page.getByRole("button", { name: "Save post" }).click();
    const saved = await (await update).json();
    expect(saved.data.version).toBe(1);
    expect(saved.data).not.toHaveProperty("summary");
    await expect(page.getByRole("status")).toHaveText("Saved.");
    await page.reload();
    await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Saved through a bound form");
    await expect(page.getByLabel("Summary (optional)")).toHaveValue("");

    const state = await (await page.request.get("/service/auth/session")).json();
    const external = await page.request.patch(`/service/editorial/posts/${id}`, {
      headers: { "X-CSRF-Token": state.csrfToken },
      data: { version: 1, title: "Changed elsewhere" }
    });
    expect(external.status()).toBe(200);
    await page.getByLabel("Title", { exact: true }).fill("Preserve my unsaved words");
    await page.getByRole("button", { name: "Save post" }).click();
    await expect(page.getByRole("alert")).toContainText("changed elsewhere");
    await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Preserve my unsaved words");
    await page.getByRole("button", { name: "Discard my edits and reload" }).click();
    await expect(page.getByLabel("Title", { exact: true })).toHaveValue("Changed elsewhere");
    await page.getByRole("link", { name: "Open preview" }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Changed elsewhere");
    await expect(page.getByRole("link", { name: "Edit post", exact: true })).toBeVisible();
  } finally {
    if (id) {
      expect(id).toMatch(/^[0-9a-f-]{36}$/);
      const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
      execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
        "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER, "-d", url.pathname.slice(1),
        "-c", `delete from public.blog_post where id = '${id}'`], {
        env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD }, stdio: ["ignore", "pipe", "pipe"]
      });
    }
  }
});
