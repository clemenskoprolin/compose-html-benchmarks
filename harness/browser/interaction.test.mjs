// Browser-level tests for the interaction timing protocol and expected DOM updates.
import assert from "node:assert/strict";
import test from "node:test";
import { chromium } from "@playwright/test";
import { interactionInPage } from "./interaction.mjs";

test("interaction timer includes a commit delayed beyond two frames", async () => {
  const browser = await chromium.launch({ channel: process.env.BENCHMARK_BROWSER_CHANNEL || "chrome", headless: true })
    .catch(() => chromium.launch({ headless: true }));
  try {
    const page = await browser.newPage();
    await page.setContent('<button id="benchmark-filter">Filter</button><div class="items"></div>');
    await page.evaluate(() => {
      const items = document.querySelector(".items");
      items.innerHTML = Array.from({ length: 1000 }, (_, i) => `<article>${i}</article>`).join("");
      document.querySelector("button").onclick = () => setTimeout(() => {
        for (const item of [...items.children]) {
          const id = Number(item.textContent);
          if (id >= 20 && id <= 600) item.remove();
        }
      }, 150);
    });
    const duration = await page.evaluate(interactionInPage, {
      selector: "#benchmark-filter", scenario: "filter-list", operationCount: 1,
    });
    assert(duration >= 150, `Timer ended before the delayed commit: ${duration} ms`);
    assert.equal(await page.locator("article").count(), 419);
    await assert.rejects(page.evaluate(interactionInPage, {
      selector: "#missing", scenario: "filter-list", operationCount: 1,
    }), /Missing interaction trigger/);
  } finally {
    await browser.close();
  }
});
