import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/browser",
  workers: 1,
  projects: [
    { name: "contracts", testMatch: ["frontend.spec.mjs", "account.spec.mjs", "recovery.spec.mjs", "editorial.spec.mjs", "post-editor.spec.mjs", "post-search.spec.mjs", "relationships.spec.mjs", "media.spec.mjs", "post-document.spec.mjs", "i18n.spec.mjs", "translations.spec.mjs"] },
    { name: "search", testMatch: "post-search-database.spec.mjs" },
    { name: "forms", testMatch: "post-editor-database.spec.mjs" },
    { name: "changes", testMatch: "changes-database.spec.mjs" },
    { name: "editorial", testMatch: "editorial-database.spec.mjs" },
    { name: "database", testMatch: "database.spec.mjs" },
    { name: "authentication", testMatch: "auth-database.spec.mjs" },
    { name: "recovery", testMatch: "recovery-database.spec.mjs" },
    { name: "relationships", testMatch: "relationships-database.spec.mjs" },
    { name: "media", testMatch: "media-database.spec.mjs" },
    { name: "documents", testMatch: "post-document-database.spec.mjs" },
    { name: "translations", testMatch: "translations-database.spec.mjs" },
  ],
  use: {
    baseURL: "http://127.0.0.1:18080",
    browserName: "chromium",
    viewport: { width: 1440, height: 1000 },
    trace: "retain-on-failure",
  },
  webServer: {
    command: 'sbt --server "application-backend/run"',
    url: "http://127.0.0.1:18080/",
    env: { BLOG_PORT: "18080", BLOG_COOKIE_SECURE: "false", BLOG_SSR_ENABLED: "false" },
    reuseExistingServer: false,
    timeout: 90_000,
  },
});
