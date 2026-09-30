import { test, expect } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";

const ids = Array.from({ length: 6 }, () => randomUUID());
const prefix = "ssr-" + ids[0];
const translated = prefix + "-translated";
const fallback = prefix + "-fallback";
const draft = prefix + "-draft";
const title = 'Server <script>alert("unsafe")</script> & reader';
const body = "## Shared document\n\nAlready readable without JavaScript.\n\n```scala\nval answer = 42\n```";
const quoted = value => "'" + value.replaceAll("'", "''") + "'";

function sql(statement) {
  const url = new URL(process.env.BLOG_DB_URL.replace(/^jdbc:/, ""));
  execFileSync(process.env.BLOG_PSQL ?? "psql", ["-X", "-v", "ON_ERROR_STOP=1", "-q",
    "-h", url.hostname, "-p", url.port || "5432", "-U", process.env.BLOG_DB_USER,
    "-d", url.pathname.slice(1), "-c", statement], {
    env: { ...process.env, PGPASSWORD: process.env.BLOG_DB_PASSWORD },
    stdio: ["ignore", "pipe", "pipe"],
  });
}

test.beforeAll(() => {
  sql("begin; insert into public.blog_post " +
    "(id, version, slug, title, content, content_format, summary, status, published_at) values " +
    [ [ids[0], translated, title, "PUBLISHED"], [ids[1], fallback, "English fallback", "PUBLISHED"],
      [ids[2], draft, "Private SSR draft", "DRAFT"] ].map(([id, slug, heading, status]) =>
      "(" + [quoted(id), "0", quoted(slug), quoted(heading), quoted(body), "'MARKDOWN'", "'Source summary'",
        quoted(status), status === "PUBLISHED" ? "now()" : "null"].join(",") + ")").join(",") + ";" +
    "insert into public.blog_post_translation (id,version,post_id,locale,title,content,published) values (" +
    [quoted(ids[3]), "0", quoted(ids[0]), "'de'", "'Ein Artikel vom Server'",
      quoted("## Gemeinsames Dokument\n\nSchon ohne JavaScript lesbar."), "true"].join(",") + "); commit;");
  sql("insert into public.blog_post_translation (id,version,post_id,locale,title,content,published) values " +
    [[ids[4], ids[1], false], [ids[5], ids[2], true]].map(([id, post, published]) =>
      "(" + [quoted(id), "0", quoted(post), "'de'", "'Private German text'",
        "'Not public.'", published ? "true" : "false"].join(",") + ")").join(","));
});

test.afterAll(() => {
  sql("begin; delete from public.blog_post_translation where id in (" + ids.slice(3).map(quoted).join(",") + ")" +
    "; delete from public.blog_post where id in (" + ids.slice(0, 3).map(quoted).join(",") + "); commit;");
});

test("list, query and pagination are present before JavaScript runs", async ({ page }) => {
  const response = await page.goto("/en?q=" + prefix + "&sort=title&limit=1");
  expect(response.status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.locator(".post")).toHaveCount(1);
  await expect(page.getByRole("link", { name: "English fallback", exact: true })).toBeVisible();
  await expect(page.locator(".list-note")).toHaveText("Showing 1 of 2 posts");
  await page.getByRole("link", { name: "Next page", exact: true }).click();
  await expect(page.getByRole("link", { name: title, exact: true })).toBeVisible();
  await expect(page.getByText("Private SSR draft", { exact: true })).toHaveCount(0);
});

test("the same detail component renders escaped titles and structured Markdown", async ({ page }, info) => {
  const response = await page.goto("/en/posts/" + translated);
  expect(response.status()).toBe(200);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
  await expect(page.getByRole("heading", { name: "Shared document", exact: true })).toBeVisible();
  await expect(page.locator(".post-content pre")).toContainText("val answer = 42");
  expect(await response.text()).not.toContain('<script>alert("unsafe")</script>');
  await expect(page.locator('link[rel="stylesheet"]')).toHaveCount(2);
  await page.screenshot({ path: info.outputPath("ssr-english.png"), fullPage: true });
});

test("German UI, published translation and whole-article fallback agree", async ({ page }, info) => {
  expect((await page.goto("/de/posts/" + translated)).status()).toBe(200);
  await expect(page.locator("html")).toHaveAttribute("lang", "de");
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Ein Artikel vom Server");
  await expect(page.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "de");
  await expect(page.getByRole("heading", { name: "Gemeinsames Dokument" })).toBeVisible();
  await expect(page.locator(".detail-summary")).toHaveCount(0);
  await page.screenshot({ path: info.outputPath("ssr-german.png"), fullPage: true });
  await page.goto("/de/posts/" + fallback);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("English fallback");
  await expect(page.getByRole("heading", { level: 1 })).toHaveAttribute("lang", "en");
  await expect(page.locator(".translation-fallback")).toContainText("noch nicht veröffentlicht");
  await expect(page.locator(".detail-summary")).toHaveText("Source summary");
  await page.goto("/de?q=" + encodeURIComponent("Ein Artikel vom Server"));
  await expect(page.getByRole("link", { name: "Ein Artikel vom Server", exact: true })).toBeVisible();
});

test("document status, HEAD, private shell and asset boundaries stay correct", async ({ page, request }) => {
  for (const slug of [draft, prefix + "-unknown"]) {
    expect((await page.goto("/en/posts/" + slug)).status()).toBe(404);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Post not found");
    const head = await request.head("/en/posts/" + slug);
    expect(head.status()).toBe(404);
    expect(await head.text()).toBe("");
  }
  expect((await page.goto("/de?limit=0")).status()).toBe(400);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Ungültige Seite");
  expect((await request.get("/de/unavailable")).status()).toBe(503);
  expect((await request.get("/ssr/main.js")).status()).toBe(404);
  expect((await request.post("/en/posts/" + translated)).status()).toBe(405);
  const privatePage = await request.get("/de/editorial/new");
  expect(privatePage.status()).toBe(200);
  expect(privatePage.headers()["cache-control"]).toBe("no-store");
  expect(await privatePage.text()).toContain('<div id="app"></div>');
  const publicPage = await request.get("/de/posts/" + translated, {
    headers: { Cookie: "BLOGSESSIONID=untrusted-session" },
  });
  expect(publicPage.status()).toBe(200);
  expect(publicPage.headers()["cache-control"]).toBe("no-store");
  expect(publicPage.headers()["set-cookie"]).toBeUndefined();
});

async function holdBrowserStart(page, path) {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.route("**/main.js", async route => {
    await gate;
    await route.continue();
  });
  await page.goto(path, { waitUntil: "commit" });
  await page.locator("#app > .blog").waitFor();
  return async () => {
    release();
    await page.evaluate(async () => (await import("/main.js")).boot());
    await page.unroute("**/main.js");
  };
}


test("public metadata describes the selected content and only published translations", async ({ page }) => {
  const origin = process.env.BLOG_PUBLIC_ORIGIN ?? "http://127.0.0.1:18080";
  await page.goto("/en/posts/" + translated);
  await expect(page).toHaveTitle(title + " — Anjunar Journal");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", origin + "/en/posts/" + translated);
  await expect(page.locator('meta[name="description"]')).toHaveAttribute("content", "Source summary");
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute("content", title);
  await expect(page.locator('link[hreflang="de"]')).toHaveAttribute("href", origin + "/de/posts/" + translated);
  await page.goto("/de/posts/" + translated);
  await expect(page).toHaveTitle("Ein Artikel vom Server — Anjunar Journal");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", origin + "/de/posts/" + translated);
  await expect(page.locator('meta[name="description"]')).toHaveAttribute("content", "Ein Artikel vom Server");
  await expect(page.locator('link[hreflang="en"]')).toHaveAttribute("href", origin + "/en/posts/" + translated);
  await expect(page.locator('link[hreflang="x-default"]')).toHaveAttribute("href", origin + "/en/posts/" + translated);
  await expect(page.locator('link[type="application/atom+xml"]')).toHaveAttribute("href", origin + "/de/feed.xml");
  await page.goto("/de/posts/" + fallback);
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", origin + "/en/posts/" + fallback);
  await expect(page.locator('link[hreflang="de"]')).toHaveCount(0);
  await expect(page.locator('meta[name="description"]')).toHaveAttribute("content", "Source summary");
});

test("search, pagination and errors have distinct canonical and indexing policies", async ({ page, request }) => {
  const origin = process.env.BLOG_PUBLIC_ORIGIN ?? "http://127.0.0.1:18080";
  await page.goto("/en?offset=0&limit=20&tracking=ignored");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", origin + "/en");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", "index, follow");
  await page.goto("/de?q=" + prefix + "&sort=title");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", "noindex, follow");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", origin + "/de?q=" + prefix + "&sort=title");
  expect((await page.goto("/de/this-route-does-not-exist")).status()).toBe(404);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Beitrag nicht gefunden");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", "noindex, follow");
  await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);
  const head = await request.head("/de/this-route-does-not-exist");
  expect(head.status()).toBe(404);
  expect(head.headers()["x-robots-tag"]).toBe("noindex");
  expect(await head.text()).toBe("");
  expect((await request.get("/en/account")).headers()["x-robots-tag"]).toBe("noindex");
});

test("public aliases redirect once and preserve their query without accepting another origin", async ({ request }) => {
  const paths = [
    ["/?offset=20", "/en?offset=20"],
    ["/index.html?q=Scala%20%26%20CDI", "/en?q=Scala%20%26%20CDI"],
    ["/de/?sort=title", "/de?sort=title"],
    ["/posts/" + translated, "/en/posts/" + translated],
    ["/de/posts/" + translated + "/", "/de/posts/" + translated],
    ["/feed.xml", "/en/feed.xml"],
  ];
  for (const [path, location] of paths) {
    const result = await request.get(path, { maxRedirects: 0 });
    expect(result.status(), path).toBe(308);
    expect(result.headers().location).toBe(location);
    const head = await request.head(path, { maxRedirects: 0 });
    expect(head.status()).toBe(308);
    expect(await head.text()).toBe("");
  }
  expect((await request.get("/fr/posts/" + translated)).status()).toBe(404);
  expect((await request.get("/en/missing.js")).status()).toBe(404);
});

async function xmlDocument(page, source) {
  return page.evaluate(xml => {
    const doc = new DOMParser().parseFromString(xml, "application/xml");
    if (doc.querySelector("parsererror")) throw new Error(doc.querySelector("parsererror").textContent);
    return {
      namespace: doc.documentElement.namespaceURI,
      locations: [...doc.querySelectorAll("url > loc")].map(node => node.textContent),
      entries: [...doc.querySelectorAll("entry")].map(node => ({
        id: node.querySelector("id").textContent,
        title: node.querySelector("title").textContent,
        href: node.querySelector('link[rel="alternate"]').getAttribute("href"),
        updated: node.querySelector("updated").textContent,
        summary: node.querySelector("summary").textContent,
      })),
    };
  }, source);
}

test("sitemap and language feeds expose only canonical published pages and valid XML", async ({ page, request }) => {
  const origin = process.env.BLOG_PUBLIC_ORIGIN ?? "http://127.0.0.1:18080";
  const sitemap = await request.get("/sitemap.xml", {
    headers: { "X-Forwarded-Host": "untrusted.example", "X-Forwarded-Proto": "http" },
  });
  expect(sitemap.status()).toBe(200);
  expect(sitemap.headers()["content-type"]).toContain("application/xml");
  const map = await xmlDocument(page, await sitemap.text());
  expect(map.namespace).toBe("http://www.sitemaps.org/schemas/sitemap/0.9");
  expect(map.locations).toContain(origin + "/en/posts/" + translated);
  expect(map.locations).toContain(origin + "/de/posts/" + translated);
  expect(map.locations).toContain(origin + "/en/posts/" + fallback);
  expect(map.locations).not.toContain(origin + "/de/posts/" + fallback);
  expect(map.locations.some(value => value.includes(draft) || value.includes("untrusted.example"))).toBe(false);
  for (const language of ["en", "de"]) {
    const response = await request.get("/" + language + "/feed.xml");
    expect(response.status()).toBe(200);
    expect(response.headers()["content-type"]).toContain("application/atom+xml");
    const feed = await xmlDocument(page, await response.text());
    expect(feed.namespace).toBe("http://www.w3.org/2005/Atom");
    expect(feed.entries.length).toBeLessThanOrEqual(20);
    const item = feed.entries.find(entry => entry.href === origin + "/" + language + "/posts/" + translated);
    expect(item.title).toBe(language === "en" ? title : "Ein Artikel vom Server");
    expect(item.id).toBe("urn:uuid:" + ids[language === "en" ? 0 : 3]);
    expect(item.summary).toBe(language === "en" ? "Source summary" : "Ein Artikel vom Server");
    expect(feed.entries.some(entry => entry.href.includes(draft))).toBe(false);
    expect(feed.entries.some(entry => entry.title === "Private German text")).toBe(false);
    if (language === "de") expect(feed.entries.some(entry => entry.href.includes(fallback))).toBe(false);
  }
  for (const path of ["/sitemap.xml", "/en/feed.xml", "/de/feed.xml", "/robots.txt"]) {
    const head = await request.head(path);
    expect(head.status(), path).toBe(200);
    expect(await head.text()).toBe("");
    expect((await request.post(path)).status()).toBe(405);
  }
  expect(await (await request.get("/robots.txt")).text()).toContain("Sitemap: " + origin + "/sitemap.xml");
});

test("retracting a translation removes it from discovery and fallback metadata immediately", async ({ page, request }) => {
  sql("update public.blog_post_translation set published = false where id = " + quoted(ids[3]));
  try {
    const sitemap = await (await request.get("/sitemap.xml")).text();
    expect(sitemap).toContain("/en/posts/" + translated);
    expect(sitemap).not.toContain("/de/posts/" + translated);
    expect(await (await request.get("/de/feed.xml")).text()).not.toContain("urn:uuid:" + ids[3]);
    await page.goto("/de/posts/" + translated);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
    await expect(page.locator('link[hreflang="de"]')).toHaveCount(0);
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", /\/en\/posts\//);
  } finally {
    sql("update public.blog_post_translation set published = true where id = " + quoted(ids[3]));
  }
});

test.describe("hydration", () => {
  test.use({ javaScriptEnabled: true });


  test("head entries follow client navigation without duplicates or stale article metadata", async ({ page }) => {
    const origin = process.env.BLOG_PUBLIC_ORIGIN ?? "http://127.0.0.1:18080";
    const warnings = [];
    page.on("console", value => { if (value.type() === "warning") warnings.push(value.text()); });
    await page.goto("/en/posts/" + translated);
    await page.evaluate(async () => (await import("/main.js")).boot());
    await expect(page.locator("title")).toHaveCount(1);
    await expect(page.locator('link[rel="canonical"]')).toHaveCount(1);
    await page.getByRole("button", { name: "Switch to German" }).click();
    await expect(page).toHaveTitle("Ein Artikel vom Server — Anjunar Journal");
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", origin + "/de/posts/" + translated);
    await page.getByRole("link", { name: "Zu den neuesten Beiträgen", exact: true }).click();
    await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", origin + "/de");
    await expect(page.locator('meta[property="og:type"]')).toHaveAttribute("content", "website");
    await expect(page.locator('meta[property="article:published_time"]')).toHaveCount(0);
    await page.getByRole("link", { name: "Konto", exact: true }).click();
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", "noindex, follow");
    await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);
    await expect(page.locator('meta[property="og:title"]')).toHaveCount(0);
    await expect(page.locator("title")).toHaveCount(1);
    expect(warnings).toEqual([]);
  });

  test("claims the existing list and preserves input typed before startup", async ({ page }) => {
    const requests = [];
    const warnings = [];
    page.on("request", value => { if (value.url().includes("/service/blog/posts")) requests.push(value.url()); });
    page.on("console", value => { if (value.type() === "warning") warnings.push(value.text()); });
    const resume = await holdBrowserStart(page, "/en?q=" + prefix);
    await page.evaluate(() => {
      window.beforeHydration = {
        root: document.querySelector(".blog"),
        list: document.querySelector("#post-list"),
        input: document.querySelector("#post-query"),
      };
    });
    await page.getByLabel("Search posts", { exact: true }).fill("already typing");
    await resume();
    expect(await page.evaluate(() => {
      const before = window.beforeHydration;
      return before.root === document.querySelector(".blog") &&
        before.list === document.querySelector("#post-list") &&
        before.input === document.querySelector("#post-query");
    })).toBe(true);
    await expect(page.getByLabel("Search posts", { exact: true })).toHaveValue("already typing");
    await expect(page.getByRole("button", { name: "Switch to German" })).toBeDisabled();
    await expect(page.locator("#application-state")).toHaveCount(0);
    expect(requests).toEqual([]);
    expect(warnings).toEqual([]);
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await expect(page).toHaveURL(/q=already%20typing/);
    expect(requests).toHaveLength(1);
  });

  test("claims translated Markdown and starts only once", async ({ page }) => {
    const requests = [];
    page.on("request", value => { if (value.url().includes("/service/blog/posts")) requests.push(value.url()); });
    const resume = await holdBrowserStart(page, "/de/posts/" + translated);
    await page.evaluate(() => {
      window.beforeHydration = {
        article: document.querySelector(".post-detail"),
        heading: document.querySelector("h1"),
        content: document.querySelector(".post-content"),
      };
    });
    const encoded = await page.locator("#application-state").getAttribute("data-state");
    expect(encoded).not.toContain("<script");
    const state = JSON.parse(decodeURIComponent(encoded));
    expect(state.url).toBe("/de/posts/" + translated);
    expect(state.request).toBe("/service/blog/posts/" + translated + "?locale=de");
    expect(JSON.parse(state.body).data.translation.title).toBe("Ein Artikel vom Server");
    await resume();
    expect(await page.evaluate(() => {
      const before = window.beforeHydration;
      return before.article === document.querySelector(".post-detail") &&
        before.heading === document.querySelector("h1") &&
        before.content === document.querySelector(".post-content");
    })).toBe(true);
    expect(await page.evaluate(async () => {
      const app = await import("/main.js");
      return app.boot() === app.boot();
    })).toBe(true);
    expect(requests).toEqual([]);
    await page.getByRole("button", { name: "Auf Englisch wechseln" }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
    expect(requests).toHaveLength(1);
    await expect(page.locator(".blog")).toHaveCount(1);
  });

  test("reuses the rendered response once and fetches newer values on navigation", async ({ page }) => {
    const resume = await holdBrowserStart(page, "/en/posts/" + translated);
    sql("update public.blog_post set title = 'A newer server title' where id = " + quoted(ids[0]));
    try {
      await resume();
      await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
      await page.getByRole("button", { name: "Switch to German" }).click();
      await expect(page.getByRole("heading", { level: 1 })).toHaveText("Ein Artikel vom Server");
      await page.getByRole("button", { name: "Auf Englisch wechseln" }).click();
      await expect(page.getByRole("heading", { level: 1 })).toHaveText("A newer server title");
    } finally {
      sql("update public.blog_post set title = " + quoted(title) + " where id = " + quoted(ids[0]));
    }
  });

  test("hydrates error routes without retrying their initial failed request", async ({ page }) => {
    const requests = [];
    page.on("request", value => { if (value.url().includes("/service/blog/posts")) requests.push(value.url()); });
    for (const path of ["/en/posts/" + draft, "/de?limit=0", "/de/unavailable"]) {
      const resume = await holdBrowserStart(page, path);
      await page.evaluate(() => { window.beforeHeading = document.querySelector("h1"); });
      await resume();
      expect(await page.evaluate(() => window.beforeHeading === document.querySelector("h1"))).toBe(true);
      await expect(page.locator("#application-state")).toHaveCount(0);
    }
    expect(requests).toEqual([]);
  });

  for (const problem of ["corrupt", "wrong-url", "missing", "markup"]) {
    test("recovers once from " + problem + " initial state or markup", async ({ page }) => {
      const warnings = [];
      page.on("console", value => { if (value.type() === "warning") warnings.push(value.text()); });
      const resume = await holdBrowserStart(page, "/en?q=" + prefix);
      await page.evaluate(problem => {
        const state = document.querySelector("#application-state");
        if (problem === "corrupt") state.setAttribute("data-state", "%invalid");
        if (problem === "wrong-url") {
          const saved = JSON.parse(decodeURIComponent(state.getAttribute("data-state")));
          saved.url = "/de";
          state.setAttribute("data-state", encodeURIComponent(JSON.stringify(saved)));
        }
        if (problem === "missing") state.remove();
        if (problem === "markup") {
          const header = document.querySelector(".site-header");
          const replacement = document.createElement("section");
          replacement.replaceChildren(...header.childNodes);
          header.replaceWith(replacement);
        }
      }, problem);
      await resume();
      await expect(page.locator(".blog")).toHaveCount(1);
      const toggle = page.getByRole("button", { name: "Show summaries", exact: true });
      await toggle.click();
      await expect(toggle).toHaveAttribute("aria-pressed", "false");
      await expect(page.locator("#application-state")).toHaveCount(0);
      expect(warnings.filter(value => value.startsWith("Hydration failed;"))).toHaveLength(problem === "missing" ? 0 : 1);
    });
  }
});

test.describe("browser takeover", () => {
  test.use({ javaScriptEnabled: true });
  test("hydrates one page and keeps normal client navigation and controls", async ({ page }) => {
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    const initialRequests = [];
    page.on("request", value => { if (value.url().includes("/service/blog/posts")) initialRequests.push(value.url()); });
    const document = await page.goto("/en?q=" + prefix);
    expect(await document.text()).toContain("English fallback");
    await page.evaluate(async () => (await import("/main.js")).boot());
    expect(initialRequests).toEqual([]);
    await expect(page.locator(".blog")).toHaveCount(1);
    const toggle = page.getByRole("button", { name: "Show summaries", exact: true });
    await toggle.click();
    await expect(toggle).toHaveAttribute("aria-pressed", "false");
    await page.getByRole("link", { name: title, exact: true }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
    await page.getByRole("button", { name: "Switch to German" }).click();
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Ein Artikel vom Server");
    expect(errors).toEqual([]);
  });
});
