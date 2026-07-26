import { describe, expect, test } from "bun:test";
import { tryParseWithSchema } from "../../src/providers/json";
import { simplificationSchema, understandingAssessmentSchema } from "../../src/providers/schemas";
import { SECURITY_PREAMBLE } from "../../src/providers/untrusted";
import {
  assessmentSystemPrompt,
  companionSystemPrompt,
  memoryPatchSystemPrompt,
  repairSystemPrompt,
  simplificationSystemPrompt,
} from "../../src/providers/prompts";

describe("prompt separation", () => {
  test("each task prompt is distinct and includes security preamble", () => {
    const prompts = [
      simplificationSystemPrompt(1, 4),
      assessmentSystemPrompt("ru"),
      memoryPatchSystemPrompt(),
      companionSystemPrompt(),
      repairSystemPrompt(),
    ];
    for (const p of prompts) {
      expect(p.includes(SECURITY_PREAMBLE.slice(0, 40))).toBe(true);
    }
    expect(simplificationSystemPrompt(1, 4)).not.toEqual(assessmentSystemPrompt("ru"));
    expect(companionSystemPrompt()).not.toEqual(memoryPatchSystemPrompt());
  });
});

describe("mock LLM response validation", () => {
  test("accepts valid simplification JSON", () => {
    const raw = JSON.stringify({
      simplifiedChinese: "李明站在江边。有人问他是不是春秋蝉◆（故事中的一种特殊蛊虫）传人。",
      terminologyNotes: ["春秋蝉"],
      uncertaintyNotes: [],
    });
    const parsed = tryParseWithSchema(simplificationSchema, raw);
    expect(parsed.ok).toBe(true);
  });

  test("accepts valid assessment JSON", () => {
    const raw = JSON.stringify({
      score: 2,
      label: "main_idea",
      coreMeaning: "correct",
      importantDetails: "partial",
      toneAndImplication: "not_detected",
      feedbackInNativeLanguage: "Вы поняли главную сцену.",
      correctedUnderstandingInNativeLanguage: "Ли Мин стоит у реки и встречает Ван Хэна.",
      keyClueInChinese: "站在石阶上",
      ambiguityNote: null,
      nextQuestionInChinese: "王衡为什么提醒他晚上不要出门？",
      nextQuestionInNativeLanguage: "Почему Ван Хэн предупреждает его?",
      shouldContinueQuestioning: true,
    });
    const parsed = tryParseWithSchema(understandingAssessmentSchema, raw);
    expect(parsed.ok).toBe(true);
  });

  test("one repair simulation: malformed then fixed", () => {
    const bad = tryParseWithSchema(simplificationSchema, "not json");
    expect(bad.ok).toBe(false);
    const repaired = tryParseWithSchema(
      simplificationSchema,
      'Here:\n```json\n{"simplifiedChinese":"你好。","terminologyNotes":[],"uncertaintyNotes":[]}\n```',
    );
    expect(repaired.ok).toBe(true);
  });
});
