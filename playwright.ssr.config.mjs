import { defineConfig } from "@playwright/test";
import base from "./playwright.config.mjs";

export default defineConfig({
  ...base,
  timeout: 60_000,
  projects: [{ name: "ssr", testMatch: "ssr.spec.mjs" }],
  use: { ...base.use, javaScriptEnabled: false },
  webServer: {
    ...base.webServer,
    url: "http://127.0.0.1:18080/service/health/live",
    env: { ...base.webServer.env, BLOG_SSR_ENABLED: "true" },
  },
});
