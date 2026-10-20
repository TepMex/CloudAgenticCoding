import { parseGeneratedLesson, parseGlossPayload, parseGradePayload, generationMessages, glossMessages, gradeMessages } from "./lesson.ts";
import { chatCompletion } from "./openai.ts";
import { parseJsonLoose } from "./json.ts";
import type { Gloss, Lesson, Review, Settings } from "./types.ts";

function timeout(): AbortSignal {
  return AbortSignal.timeout(45_000);
}

export async function pingProvider(settings: Settings): Promise<string> {
  return chatCompletion({
    baseUrl: settings.apiBaseUrl,
    apiKey: settings.apiKey,
    model: settings.model.trim() || "gpt-4o",
    temperature: 0,
    signal: timeout(),
    messages: [{ role: "user", content: "Reply with the single word ok." }],
  });
}

export async function generateLesson(settings: Settings, passage: string, bookId: string, chapterTitle: string, fragmentTitle: string): Promise<{ lesson: Lesson; glosses: Gloss[] }> {
  const messages = generationMessages(settings, passage);
  const first = await chatCompletion({
    baseUrl: settings.apiBaseUrl,
    apiKey: settings.apiKey,
    model: settings.model,
    messages,
    temperature: 0.7,
    signal: timeout(),
  });
  try {
    return parseGeneratedLesson(parseJsonLoose(first), bookId, chapterTitle, fragmentTitle);
  } catch {
    const second = await chatCompletion({
      baseUrl: settings.apiBaseUrl,
      apiKey: settings.apiKey,
      model: settings.model,
      temperature: 0,
      signal: timeout(),
      messages: [
        ...messages,
        { role: "assistant", content: first.slice(0, 6000) },
        { role: "user", content: "Return only the JSON object, with no markdown." },
      ],
    });
    return parseGeneratedLesson(parseJsonLoose(second), bookId, chapterTitle, fragmentTitle);
  }
}

export async function gradeWithModel(
  settings: Settings,
  lesson: Lesson,
  answers: Record<string, string>,
  difficulty: string,
  comment: string,
): Promise<Review> {
  const messages = gradeMessages(settings, lesson, answers, difficulty, comment);
  const first = await chatCompletion({
    baseUrl: settings.apiBaseUrl,
    apiKey: settings.apiKey,
    model: settings.model,
    messages,
    temperature: 0.3,
    signal: timeout(),
  });
  let parsed: unknown;
  try {
    parsed = parseJsonLoose(first);
  } catch {
    const second = await chatCompletion({
      baseUrl: settings.apiBaseUrl,
      apiKey: settings.apiKey,
      model: settings.model,
      temperature: 0,
      signal: timeout(),
      messages: [
        ...messages,
        { role: "assistant", content: first.slice(0, 6000) },
        { role: "user", content: "Return only the JSON object, with no markdown." },
      ],
    });
    parsed = parseJsonLoose(second);
  }
  const grade = parseGradePayload(parsed, lesson);
  return {
    items: grade.items,
    keyMoments: grade.keyMoments,
    source: "model",
  };
}

export async function lookupGloss(settings: Settings, hanzi: string, sentence: string): Promise<Gloss> {
  const content = await chatCompletion({
    baseUrl: settings.apiBaseUrl,
    apiKey: settings.apiKey,
    model: settings.model,
    messages: glossMessages(settings, hanzi, sentence),
    temperature: 0.3,
    signal: timeout(),
  });
  return parseGlossPayload(parseJsonLoose(content), hanzi);
}
