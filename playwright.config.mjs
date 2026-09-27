import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/browser",
  workers: 1,
  use: {
    baseURL: "http://127.0.0.1:18080",
    browserName: "chromium",
    viewport: { width: 1440, height: 1000 },
    trace: "retain-on-failure",
  },
  webServer: {
    command: 'sbt --server "application-backend/run"',
    url: "http://127.0.0.1:18080/",
    env: { BLOG_PORT: "18080" },
    reuseExistingServer: false,
    timeout: 90_000,
  },
});
