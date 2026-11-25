import { describe, expect, test } from "bun:test";
import { resolveAppUrl } from "../src/lib/app-url.ts";

describe("resolveAppUrl", () => {
  test("dev server root", () => {
    expect(resolveAppUrl("http://127.0.0.1:4731/", "/corpus.json")).toBe("http://127.0.0.1:4731/corpus.json");
    expect(resolveAppUrl("http://127.0.0.1:4731/", "vendor/litertlm/")).toBe(
      "http://127.0.0.1:4731/vendor/litertlm/",
    );
  });

  test("GitHub Pages directory and index.html", () => {
    const dir = "https://tepmex.github.io/CloudAgenticCoding/ffmpeg-commands/";
    expect(resolveAppUrl(dir, "/embeddings.f32")).toBe(`${dir}embeddings.f32`);
    expect(resolveAppUrl(dir, "/vendor/litertlm/")).toBe(`${dir}vendor/litertlm/`);
    expect(resolveAppUrl(`${dir}index.html`, "embeddings.json")).toBe(`${dir}embeddings.json`);
  });
});
