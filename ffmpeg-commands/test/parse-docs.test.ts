import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import path from "node:path";
import { buildEmbedText, intentHints } from "../src/lib/embed-text.ts";
import { commandsFromPre, decodeEntities, joinContinuations } from "../src/lib/html-text.ts";
import { parseFfmpegPage } from "../src/lib/parse-docs.ts";
import { cosineSimilarity, diversify, rankChunks } from "../src/lib/rank.ts";
import type { Chunk, SearchHit } from "../src/lib/types.ts";

const fixture = readFileSync(path.join(import.meta.dir, "fixtures/sample.html"), "utf8");

test("decodes entities and joins continued commands", () => {
  expect(decodeEntities("ffmpeg -vf &quot;scale=2&quot; &amp; more")).toBe('ffmpeg -vf "scale=2" & more');
  const lines = joinContinuations("ffmpeg -i input \\\n  -c:v libx264 \\\n  output.mp4\n\nffmpeg -version");
  expect(lines).toEqual(["ffmpeg -i input -c:v libx264 output.mp4", "ffmpeg -version"]);
});

test("parses a filter, its example, and a CLI option", () => {
  const chunks = parseFfmpegPage({
    html: fixture,
    page: "filters",
    url: "https://ffmpeg.org/ffmpeg-filters.html",
  });
  const volume = chunks.find(chunk => chunk.kind === "component" && chunk.title === "volume");
  expect(volume?.family).toBe("audio-filter");
  expect(volume?.command).toBe("ffmpeg -i input.wav -af volume=0.5 output.wav");
  expect(volume?.synthesized).toBe(false);
  expect(volume?.url).toBe("https://ffmpeg.org/ffmpeg-filters.html#volume");
  expect(volume?.embedText).toContain("громкость");

  const example = chunks.find(chunk => chunk.kind === "example" && chunk.command?.includes("volume=0.5"));
  expect(example?.summary.toLowerCase()).toContain("halve");

  const scale = chunks.find(chunk => chunk.kind === "component" && chunk.title === "scale");
  expect(scale?.synthesized).toBe(true);
  expect(scale?.command).toBe("ffmpeg -i input.mp4 -vf scale=1280:720 output.mp4");

  const optionChunks = parseFfmpegPage({
    html: fixture,
    page: "ffmpeg",
    url: "https://ffmpeg.org/ffmpeg.html",
  });
  const seek = optionChunks.find(chunk => chunk.kind === "option" && chunk.title === "-ss");
  expect(seek?.command).toContain("-c copy");
  expect(seek?.embedText).toContain("вырезать");
  expect(optionChunks.find(chunk => chunk.title === "Examples")).toBeUndefined();
});

test("task hints follow the command, not an incidental word in the manual", () => {
  expect(intentHints("silencedetect\nffmpeg -i silence.mp3 -af silencedetect=noise=0.0001 -f null -").join(" ")).not.toContain("тише");
  expect(intentHints("volume\nffmpeg -i input.wav -af volume=0.5 output.wav").join(" ")).toContain("тише");
  expect(intentHints("scale_d3d12\nffmpeg -i input.mp4 -vf scale_d3d12 output.mp4").join(" ")).not.toContain("разрешение");
  expect(intentHints("scale\nffmpeg -i input.mp4 -vf scale=1280:720 output.mp4").join(" ")).toContain("разрешение");
});

test("keeps task hints when the manual paragraph is long", () => {
  const text = buildEmbedText({
    family: "option",
    title: "-ss (Main options)",
    summary: "seek ".repeat(200),
    command: "ffmpeg -ss 00:01:20 -i input.mp4 -t 30 -c copy output.mp4",
    signature: "-ss position (input/output)",
  });
  expect(text).toContain("вырезать");
  expect(text).toContain("ffmpeg -ss 00:01:20");
  expect(text.length).toBeLessThanOrEqual(900);
});

test("strips a comma from a combined flag and fills a missing example", () => {
  const html = `<h2>3 Options</h2><h3>3.1 Main options<a href="#Main-options">#</a></h3>
<dl><dt><samp class="option">-L, -license</samp></dt><dd><p>Show license.</p></dd>
<dt><samp class="option">-ss position</samp></dt><dd><p>Seek to position.</p></dd></dl>`;
  const chunks = parseFfmpegPage({ html, page: "ffmpeg", url: "https://ffmpeg.org/ffmpeg.html" });
  expect(chunks.find(chunk => chunk.kind === "option" && chunk.summary.includes("license"))?.title).toBe("-L");
  const seek = chunks.find(chunk => chunk.title === "-ss");
  expect(seek?.synthesized).toBe(true);
  expect(seek?.command).toContain("-c copy");
});

test("extracts multiline pre commands", () => {
  const commands = commandsFromPre(
    "ffmpeg -i INPUT \\\n  -vf scale=640:360 \\\n  OUTPUT\n",
  );
  expect(commands).toEqual(["ffmpeg -i INPUT -vf scale=640:360 OUTPUT"]);
});

test("ranks by cosine and hides duplicate commands", () => {
  const chunks: Chunk[] = [
    chunk("a", "ffmpeg -i input.wav -af volume=0.5 output.wav"),
    chunk("b", "ffmpeg -i input.mp4 -c:v libx264 output.mp4"),
    chunk("c", "ffmpeg -i input.wav -af volume=0.5 output.wav"),
  ];
  const vectors = [
    [1, 0, 0],
    [0, 1, 0],
    [0.9, 0.1, 0],
  ];
  const hits = rankChunks([1, 0, 0], chunks, vectors, 5);
  expect(hits.map(hit => hit.id)).toEqual(["a", "b"]);
  expect(hits[0]?.score).toBeGreaterThan(hits[1]?.score ?? 0);
  expect(cosineSimilarity([1, 0], [0, 1])).toBe(0);
});

test("diversify keeps the higher score first", () => {
  const hits: SearchHit[] = [
    hit("1", "ffmpeg -i a output", 0.9),
    hit("2", "ffmpeg -i a output", 0.8),
    hit("3", "ffmpeg -i b output", 0.7),
  ];
  expect(diversify(hits, 2).map(item => item.id)).toEqual(["1", "3"]);
});

function chunk(id: string, command: string): Chunk {
  return {
    id,
    kind: "example",
    priority: 0,
    title: id,
    summary: id,
    command,
    signature: null,
    synthesized: false,
    url: "https://ffmpeg.org/ffmpeg.html",
    page: "ffmpeg",
    family: "example",
    embedText: command,
  };
}

function hit(id: string, command: string, score: number): SearchHit {
  return {
    id,
    title: id,
    summary: "",
    command,
    signature: null,
    synthesized: false,
    url: "https://ffmpeg.org/ffmpeg.html",
    page: "ffmpeg",
    family: "example",
    kind: "example",
    score,
  };
}
