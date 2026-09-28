import { test, expect } from "@playwright/test";
import { file, png } from "./media-fixture.mjs";

const id = "8a1c1582-e841-4a27-a506-1a630337df48";
const first = "896d0270-b13b-4735-bac6-3606a0407cc0";
const second = "512d47a1-9e51-4356-a50e-7d05b00f9fcd";
const api = `/service/editorial/posts/${id}`;
const edit = `/en/editorial/posts/${id}/edit`;
const link = (rel, url, method = "GET") => ({ rel, url, method });
const media = id => ({ id, version: 0, name: "cover.png", contentType: "image/png", width: 480, height: 240, byteSize: png.length });
const reply = (route, value, status = 200) =>
  route.fulfill({ status, contentType: "application/json", body: JSON.stringify(value) });

async function setup(page, initial = {}) {
  const state = { value: { id, version: 0, slug: "media", title: "A cover image", content: "The post text", status: "DRAFT", ...initial }, writes: [] };
  await page.route("**/service/auth/session", route => reply(route, {
    csrfToken: "media-token", account: { id: "admin", role: "ADMIN" },
    $links: [link("editorial", "/service/editorial/posts"), link("uploadImage", "/service/editorial/media", "POST")]
  }));
  await page.route("**/service/media/*", route => route.fulfill({ contentType: "image/png", body: png }));
  await page.route(`**${api}`, route => {
    if (route.request().method() === "PATCH") {
      const body = route.request().postDataJSON();
      state.writes.push(body);
      state.value = { ...state.value, ...body, version: state.value.version + 1,
        coverImage: Object.hasOwn(body, "coverImage") ? (body.coverImage ? media(body.coverImage.id) : null) : state.value.coverImage };
    }
    return reply(route, { data: state.value, $links: [link("update", api, "PATCH")] });
  });
  return state;
}

test("raw upload, required description, ID-only save and explicit removal", async ({ page }, info) => {
  const state = await setup(page);
  await page.route("**/service/editorial/media", route => {
    expect(route.request().headers()["content-type"]).toBe("image/png");
    expect(route.request().headers()["x-csrf-token"]).toBe("media-token");
    expect(route.request().postDataBuffer()).toEqual(png);
    return reply(route, { data: media(first) }, 201);
  });
  await page.goto(edit);
  await page.getByLabel("Cover image", { exact: true }).setInputFiles(file);
  await expect(page.locator(".cover-preview")).toHaveAttribute("src", "/service/media/" + first);
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.locator("#post-cover-alt-errors")).toContainText("Describe the cover image.");
  expect(state.writes).toHaveLength(0);
  await page.getByLabel("Image description").fill("Blue and green panels beside the post");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(state.writes[0]).toMatchObject({ version: 0, coverImage: { id: first }, coverAlt: "Blue and green panels beside the post" });
  await page.screenshot({ path: info.outputPath("media-editor-desktop.png"), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: info.outputPath("media-editor-mobile.png"), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole("button", { name: "Remove cover image" }).click();
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Saved.");
  expect(state.writes[1]).toMatchObject({ version: 1, coverImage: null });
});

test("removing a pending upload unblocks saving and ignores its late response", async ({ page }) => {
  await setup(page);
  let release;
  const gate = new Promise(resolve => release = resolve);
  await page.route("**/service/editorial/media", async route => {
    await gate;
    await reply(route, { data: media(first) }, 201).catch(() => {});
  });
  await page.goto(edit);
  const started = page.waitForRequest("**/service/editorial/media");
  await page.getByLabel("Cover image", { exact: true }).setInputFiles(file);
  await started;
  await expect(page.getByRole("button", { name: "Save post", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Remove cover image" }).click();
  release();
  await expect(page.getByRole("button", { name: "Save post", exact: true })).toBeEnabled();
  await expect(page.locator(".cover-preview")).toHaveCount(0);
});

test("file validation and server rejection keep the selected image", async ({ page }) => {
  await setup(page, { coverImage: media(first), coverAlt: "Original cover" });
  let uploads = 0;
  await page.route("**/service/editorial/media", route => { uploads++; return reply(route, {}, 400); });
  await page.goto(edit);
  const input = page.getByLabel("Cover image", { exact: true });
  await input.setInputFiles({ name: "bad.svg", mimeType: "image/svg+xml", buffer: Buffer.from("<svg/>") });
  await expect(page.getByRole("alert")).toHaveText("Choose a JPEG or PNG image.");
  await input.setInputFiles({ ...file, buffer: Buffer.alloc(5 * 1024 * 1024 + 1) });
  await expect(page.getByRole("alert")).toContainText("too large");
  expect(uploads).toBe(0);
  await input.setInputFiles(file);
  await expect(page.getByRole("alert")).toContainText("could not be decoded");
  expect(uploads).toBe(1);
  await expect(page.locator(".cover-preview")).toHaveAttribute("src", "/service/media/" + first);
});

test("a delayed post save preserves a newer image upload and description", async ({ page }) => {
  const state = await setup(page, { coverImage: media(first), coverAlt: "Original cover" });
  let release;
  const gate = new Promise(resolve => release = resolve);
  await page.route(`**${api}`, async route => {
    if (route.request().method() !== "PATCH") return route.fallback();
    const saved = { ...state.value, ...route.request().postDataJSON(), version: 1 };
    await gate;
    await reply(route, { data: saved, $links: [link("update", api, "PATCH")] });
  });
  await page.route("**/service/editorial/media", route => reply(route, { data: media(second) }, 201));
  await page.goto(edit);
  await page.getByLabel("Title", { exact: true }).fill("Saving this title");
  const started = page.waitForRequest(request => request.method() === "PATCH");
  await page.getByRole("button", { name: "Save post", exact: true }).click();
  await started;
  await page.getByLabel("Cover image", { exact: true }).setInputFiles(file);
  await expect(page.locator(".cover-preview")).toHaveAttribute("src", "/service/media/" + second);
  await page.getByLabel("Image description").fill("A newer cover");
  release();
  await expect(page.getByRole("status")).toHaveText("Saved. Your newer edits still need saving.");
  await expect(page.locator(".cover-preview")).toHaveAttribute("src", "/service/media/" + second);
  await expect(page.getByLabel("Image description")).toHaveValue("A newer cover");
});
