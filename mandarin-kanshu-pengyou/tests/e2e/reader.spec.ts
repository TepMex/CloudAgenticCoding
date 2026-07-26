import { test, expect } from "@playwright/test";
import path from "node:path";

test.describe("primary Chromium workflow", () => {
  test("library loads and can import sample EPUB", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("看书朋友")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Library" })).toBeVisible();

    const fixture = path.join(__dirname, "../../fixtures/sample.epub");
    // Ensure fixture exists via prior bun fixture script in CI; skip if missing
    await page.locator('input[type="file"]').setInputFiles(fixture);
    await expect(page.getByText("江边小记")).toBeVisible({ timeout: 15_000 });
  });
});
