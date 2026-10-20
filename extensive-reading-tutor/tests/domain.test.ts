import { expect, test } from "bun:test";
import { parseGeneratedLessonText } from "../src/lib/lesson.ts";
import { gradeLocal, tally } from "../src/lib/grade.ts";
import { buildHeatmap } from "../src/lib/heatmap.ts";
import { extractJsonText } from "../src/lib/json.ts";
import { chatCompletionsUrl, chatCompletion, providerLabel } from "../src/lib/openai.ts";
import { hashToRoute, routeToHash } from "../src/lib/route.ts";
import { HERO_BOOK_ID, HERO_PASSAGE, seedActivity, seedLessons } from "../src/lib/seed.ts";
import { createInitialState, localReview, reducer } from "../src/lib/store.ts";
import { glossIdFor, passageOf, readingMap, tokenize } from "../src/lib/text.ts";

test("chat URL accepts host, /v1, and the full method path", () => {
  expect(chatCompletionsUrl("https://api.openai.com")).toBe("https://api.openai.com/v1/chat/completions");
  expect(chatCompletionsUrl("https://api.openai.com/")).toBe("https://api.openai.com/v1/chat/completions");
  expect(chatCompletionsUrl("https://api.openai.com/v1")).toBe("https://api.openai.com/v1/chat/completions");
  expect(chatCompletionsUrl("https://api.openai.com/v1/")).toBe("https://api.openai.com/v1/chat/completions");
  expect(chatCompletionsUrl("http://localhost:11434/v1")).toBe("http://localhost:11434/v1/chat/completions");
  expect(chatCompletionsUrl("https://openrouter.ai/api/v1/chat/completions")).toBe(
    "https://openrouter.ai/api/v1/chat/completions",
  );
  expect(providerLabel("https://api.openai.com/v1")).toBe("OpenAI");
  expect(providerLabel("http://127.0.0.1:11434/v1")).toBe("127.0.0.1:11434");
});

test("chatCompletion posts bearer token and retries without temperature", async () => {
  const calls: { url: string; temperature?: number; auth?: string }[] = [];
  const original = globalThis.fetch;
  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const body = JSON.parse(String(init?.body)) as { temperature?: number };
    const headers = new Headers(init?.headers);
    calls.push({ url: String(input), temperature: body.temperature, auth: headers.get("Authorization") ?? undefined });
    if (calls.length === 1) {
      return new Response(JSON.stringify({ error: { message: "temperature is unsupported" } }), { status: 400 });
    }
    return new Response(JSON.stringify({ choices: [{ message: { content: [{ type: "text", text: "ok" }] } }] }), {
      status: 200,
    });
  }) as typeof fetch;
  try {
    const text = await chatCompletion({
      baseUrl: "http://localhost:11434/v1",
      apiKey: "sk-test",
      model: "local-model",
      messages: [{ role: "user", content: "ping" }],
    });
    expect(text).toBe("ok");
    expect(calls.length).toBe(2);
    expect(calls[0]?.url).toBe("http://localhost:11434/v1/chat/completions");
    expect(calls[0]?.auth).toBe("Bearer sk-test");
    expect(calls[0]?.temperature).toBe(0.4);
    expect(calls[1]?.temperature).toBeUndefined();
  } finally {
    globalThis.fetch = original;
  }
});

test("extracts fenced JSON", () => {
  expect(JSON.parse(extractJsonText("sure\n```json\n{\"a\":1}\n```"))).toEqual({ a: 1 });
});

test("hero passage keeps 扑面而来 as one token", () => {
  const lesson = seedLessons.find(item => item.bookId === HERO_BOOK_ID);
  expect(lesson).toBeTruthy();
  expect(passageOf(lesson!.tokens)).toBe(HERO_PASSAGE);
  const idiom = lesson!.tokens.find(token => token.t === "扑面而来");
  expect(idiom?.glossId).toBe(glossIdFor("扑面而来"));
  const glossIds = new Set(lesson!.tokens.map(token => token.glossId).filter(Boolean));
  expect(glossIds.size).toBe(8);
  const counts = { sense: 0, words: 0, grammar: 0 };
  for (const question of lesson!.questions) counts[question.category] += 1;
  expect(counts).toEqual({ sense: 2, words: 2, grammar: 1 });
});

test("local grading distinguishes correct, partial, and empty", () => {
  const lesson = seedLessons[0]!;
  const items = gradeLocal(lesson, {
    "q-1": "B",
    "q-2": "запах резко накрывает лицо",
    "q-3": "это риторический вопрос, неужели",
    "q-4": "книги",
    "q-5": "",
  });
  expect(items.map(item => item.verdict)).toEqual(["correct", "correct", "correct", "partial", "attention"]);
  expect(tally(items)).toEqual({ correct: 3, partial: 1, attention: 1 });
});

test("parses a model lesson", () => {
  const raw = `{
    "intro": "Коротко.",
    "hints": ["Смотри на глагол."],
    "passage": "她忽然觉得，宇宙并不沉默。",
    "words": [{"hanzi":"忽然","pinyin":"hūrán","gloss":"вдруг","kind":"word","example":"他忽然笑了。","exampleGloss":"Он вдруг засмеялся."}],
    "questions": [
      {"category":"sense","title":"Смысл","prompt":"О чём?","kind":"open","explanation":"О тишине.","keywords":["тишин"]},
      {"category":"words","title":"Лексика","prompt":"忽然?","kind":"open","explanation":"вдруг","keywords":["вдруг"]},
      {"category":"grammar","title":"并不","prompt":"Что делает 并不?","kind":"open","explanation":"эмфаза отрицания","keywords":["отриц"]}
    ],
    "keyMoments": ["Вселенная не молчит."]
  }`;
  const { lesson, glosses } = parseGeneratedLessonText(raw, "book-x", "Глава 1", "Фрагмент 1");
  expect(glosses).toHaveLength(1);
  expect(lesson.questions).toHaveLength(3);
  expect(passageOf(lesson.tokens)).toContain("忽然");
  expect(lesson.tokens.some(token => token.glossId === glosses[0]?.id)).toBe(true);
});

test("routes survive a hash round-trip", () => {
  const routes = [
    { name: "library" } as const,
    { name: "study" } as const,
    { name: "profile" } as const,
    { name: "settings" } as const,
    { name: "knowledge", tab: "grammar" } as const,
    { name: "book", bookId: "book-tgcf", tab: "notes" } as const,
    { name: "lesson", bookId: "book-tgcf", phase: 3 } as const,
  ];
  for (const route of routes) {
    expect(hashToRoute(routeToHash(route))).toEqual(route);
  }
  expect(hashToRoute("#/nope")).toEqual({ name: "library" });
});

test("reading map and heatmap", () => {
  expect(readingMap(100).every(cell => cell === "done")).toBe(true);
  expect(readingMap(0)[0]).toBe("current");
  const mid = readingMap(15);
  expect(mid.filter(cell => cell === "current")).toHaveLength(1);
  expect(mid.some(cell => cell === "done")).toBe(true);
  const heat = buildHeatmap(seedActivity);
  expect(heat.cells.find(cell => cell.date === "2026-10-20")?.level).toBe(2);
  expect(heat.months).toContain("авг");
  expect(heat.months).toContain("дек");
});

test("review credits a book once", () => {
  let state = reducer(createInitialState(), { type: "ensure-attempt", bookId: HERO_BOOK_ID });
  const before = state.books.find(book => book.id === HERO_BOOK_ID)!.words;
  const lesson = state.lessons.find(item => item.bookId === HERO_BOOK_ID)!;
  const review = localReview(lesson, { "q-1": "B" });
  state = reducer(state, { type: "review", bookId: HERO_BOOK_ID, review });
  const after = state.books.find(book => book.id === HERO_BOOK_ID)!.words;
  expect(after).toBe(before + 5);
  expect(state.route).toEqual({ name: "lesson", bookId: HERO_BOOK_ID, phase: 4 });
  state = reducer(state, { type: "review", bookId: HERO_BOOK_ID, review });
  expect(state.books.find(book => book.id === HERO_BOOK_ID)!.words).toBe(after);
});

test("tokenize prefers the longer word", () => {
  const tokens = tokenize("图书馆", [
    { id: "a", hanzi: "图书" },
    { id: "b", hanzi: "图书馆" },
  ]);
  expect(tokens).toEqual([{ t: "图书馆", glossId: "b" }]);
});
