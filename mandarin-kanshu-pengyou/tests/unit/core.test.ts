import { describe, expect, test } from "bun:test";
import {
  buildExpandedSelection,
  countSentences,
  expandToSentenceBoundaries,
  nearbyParagraphs,
  normalizeMultiParagraph,
} from "../../src/reader/selection/sentences";
import {
  buildReaderLocation,
  deserializeLocation,
  recoverRangeFromLocation,
  serializeLocation,
} from "../../src/reader/locations/location";
import { extractJsonText, parseJsonLoose, tryParseWithSchema } from "../../src/providers/json";
import { z } from "zod";
import { applyMemoryPatch, mergeConfidence } from "../../src/memory/merge";
import { emptyBookMemory } from "../../src/shared/domain";
import { redactSecrets, safeErrorMessage } from "../../src/security/redact";
import { firstAttemptScores } from "../../src/statistics/stats";
import { nextSimplificationLevel } from "../../src/explain/service";

describe("sentence expansion", () => {
  const text = "甲说了一句话。乙很吃惊！丙问：发生了什么事？丁没有回答。";

  test("expands to sentence boundaries", () => {
    const start = text.indexOf("很吃惊");
    const end = start + "很吃惊".length;
    const range = expandToSentenceBoundaries(text, start, end);
    expect(text.slice(range.start, range.end)).toBe("乙很吃惊！");
  });

  test("allows multi-sentence and cross-paragraph", () => {
    const multi = "第一句。\n\n第二句！第三句？";
    const sel = buildExpandedSelection(multi, multi.indexOf("第二"), multi.indexOf("第三") + 2);
    expect(sel.expandedText.includes("第二句")).toBe(true);
    expect(sel.sentenceCount).toBeGreaterThanOrEqual(2);
  });

  test("soft limit warning", () => {
    const long = Array.from({ length: 6 }, (_, i) => `这是第${i + 1}句。`).join("");
    const sel = buildExpandedSelection(long, 0, long.length);
    expect(sel.exceedsSoftLimit).toBe(true);
    expect(countSentences(long)).toBe(6);
  });

  test("normalize multi-paragraph", () => {
    expect(normalizeMultiParagraph("甲。\n\n  乙。  \n丙。")).toContain("甲。");
  });

  test("nearby paragraphs", () => {
    const t = "A段。\n\nB段。\n\nC段。\n\nD段。\n\nE段。";
    const bStart = t.indexOf("C段");
    const near = nearbyParagraphs(t, bStart, bStart + 3, 2, 2);
    expect(near.before.includes("A段") || near.before.includes("B段")).toBe(true);
    expect(near.after.includes("D段") || near.after.includes("E段")).toBe(true);
  });
});

describe("reader locations", () => {
  test("serialize roundtrip", () => {
    const loc = buildReaderLocation({
      bookId: "b1",
      spineItemId: "c1",
      chapterText: "前缀内容选中的句子后缀内容",
      start: 4,
      end: 10,
    });
    const again = deserializeLocation(serializeLocation(loc));
    expect(again.textQuote).toBe(loc.textQuote);
  });

  test("quote recovery", () => {
    const chapter = "昨天李明去了青云谷。王衡也在那里。今天李明又去了青云谷。";
    const start = chapter.indexOf("王衡也在那里");
    const loc = buildReaderLocation({
      bookId: "b",
      spineItemId: "s",
      chapterText: chapter,
      start,
      end: start + "王衡也在那里".length,
    });
    const recovered = recoverRangeFromLocation(chapter, loc);
    expect(recovered.method).not.toBe("failed");
    expect(chapter.slice(recovered.start, recovered.end)).toBe("王衡也在那里");
  });
});

describe("json extraction", () => {
  test("fenced json", () => {
    const raw = 'Sure:\n```json\n{"ok":true,"n":1}\n```\n';
    expect(extractJsonText(raw)).toContain('"ok"');
    expect((parseJsonLoose(raw) as { ok: boolean }).ok).toBe(true);
  });

  test("schema validation", () => {
    const schema = z.object({ ok: z.boolean() });
    const good = tryParseWithSchema(schema, '{"ok":true}');
    expect(good.ok).toBe(true);
    const bad = tryParseWithSchema(schema, '{"ok":"nope"}');
    expect(bad.ok).toBe(false);
  });
});

describe("memory merge", () => {
  test("confidence merge prefers stronger", () => {
    expect(mergeConfidence("uncertain", "certain")).toBe("certain");
    expect(mergeConfidence("probable", "uncertain")).toBe("probable");
  });

  test("apply patch upserts entities and events", () => {
    const loc = {
      bookId: "b",
      spineItemId: "c1",
      textQuote: "春秋蝉",
      prefix: "",
      suffix: "",
    };
    let mem = emptyBookMemory("b");
    mem = applyMemoryPatch(
      mem,
      {
        synopsisUpdate: "A quiet riverside story.",
        entities: [
          {
            op: "upsert",
            type: "term",
            canonicalName: "春秋蝉",
            aliases: [],
            description: "故事中的一种特殊蛊虫（uncertain）",
            confidence: "uncertain",
          },
        ],
        events: [
          {
            op: "add",
            summary: "李明在江边遇到王衡",
            participants: ["李明", "王衡"],
            locationName: "江边",
            confidence: "certain",
          },
        ],
      },
      loc,
      "c1",
    );
    expect(mem.synopsis).toContain("riverside");
    expect(mem.entities[0]?.canonicalName).toBe("春秋蝉");
    expect(mem.currentChapterEvents).toHaveLength(1);
  });
});

describe("assistance levels", () => {
  test("recursive levels stop at 3", () => {
    expect(nextSimplificationLevel(1)).toBe(2);
    expect(nextSimplificationLevel(2)).toBe(3);
    expect(nextSimplificationLevel(3)).toBeNull();
  });
});

describe("assessment first-attempt stats", () => {
  test("only first attempts count", () => {
    const scores = firstAttemptScores([
      { isFirstAttemptForPassage: true, initialScore: 3 },
      { isFirstAttemptForPassage: false, initialScore: 4 },
      { isFirstAttemptForPassage: true, initialScore: 1 },
      { isFirstAttemptForPassage: true, initialScore: null },
    ]);
    expect(scores).toEqual([3, 1]);
  });
});

describe("api key redaction", () => {
  test("redacts sk- and bearer tokens", () => {
    expect(redactSecrets("key sk-abcdefghijklmnopqrstuvwxyz error")).toContain("[REDACTED]");
    expect(safeErrorMessage(new Error("Bearer abcdefghijklmnop failed"))).not.toContain(
      "abcdefghijklmnop",
    );
  });
});
