import { chatCompletion } from "./openai";
import { parseJson } from "./json";

export type QAPair = { question: string; correctAnswer: string };

export type SessionPhase =
  | { kind: "loading_block" }
  | { kind: "loading_qa" }
  | { kind: "llm_question"; question: string }
  | { kind: "awaiting_user" }
  | { kind: "loading_feedback" }
  | { kind: "show_passage"; passage: string; feedback?: string }
  | { kind: "chapter_done" }
  | { kind: "error"; message: string };

export type SessionState = {
  chapterText: string;
  /** Start index in chapterText for the current segment */
  cursor: number;
  phase: SessionPhase;
  /** Current logical block (when known) */
  currentBlock: string | null;
  currentQA: QAPair | null;
  /** After the user reads the passage, advance the cursor to this index */
  pendingNextCursor?: number;
};

const SYSTEM_BLOCK = `You are helping segment educational reading text. Given a chapter excerpt starting at the beginning marker, return where the next coherent "logical block" ends (one idea, scene, or argument unit — not the whole chapter).

Respond with JSON only: {"end_offset": <number>} where end_offset is the character index (0-based) in the provided excerpt marking the LAST character to include in this block. The excerpt uses 0-based indexing. Choose a block between roughly 400 and 4000 characters when possible; prefer natural paragraph boundaries.`;

const SYSTEM_QA = `You create reading comprehension checks. Given a passage, write ONE clear question and a concise model answer that captures the key idea.

Respond with JSON only: {"question": string, "correct_answer": string}`;

const SYSTEM_DIALOG = `You are a Socratic reading tutor. The user is reading a passage. You asked them a question; they answered. Compare their answer to the model answer. Be specific about what matches, what's missing, and any misconceptions. Keep it encouraging and concise (under 200 words). Do not repeat the full passage.`;

function excerptFromCursor(full: string, cursor: number, maxLen = 12000): string {
  return full.slice(cursor, cursor + maxLen);
}

export async function detectBlockEnd(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  chapterText: string;
  cursor: number;
}): Promise<{ block: string; nextCursor: number }> {
  const { baseUrl, apiKey, model, chapterText, cursor } = params;
  if (cursor >= chapterText.length) {
    return { block: "", nextCursor: cursor };
  }

  const excerpt = excerptFromCursor(chapterText, cursor);
  const user = `Excerpt (characters 0..${excerpt.length - 1} of this slice only):\n\n${excerpt}`;

  const raw = await chatCompletion({
    baseUrl,
    apiKey,
    model,
    messages: [
      { role: "system", content: SYSTEM_BLOCK },
      { role: "user", content: user },
    ],
    temperature: 0.2,
  });

  const parsed = parseJson<{ end_offset?: number }>(raw);
  const end = typeof parsed.end_offset === "number" ? parsed.end_offset : Number.NaN;
  if (!Number.isFinite(end) || end < 0) {
    throw new Error("Model returned invalid end_offset");
  }

  const clampedEnd = Math.min(Math.max(end, 0), excerpt.length - 1);
  const block = excerpt.slice(0, clampedEnd + 1).trim();
  if (!block) {
    throw new Error("Empty logical block from model");
  }

  const nextCursor = cursor + clampedEnd + 1;
  return { block, nextCursor };
}

export async function generateQA(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  passage: string;
}): Promise<QAPair> {
  const { baseUrl, apiKey, model, passage } = params;
  const raw = await chatCompletion({
    baseUrl,
    apiKey,
    model,
    messages: [
      { role: "system", content: SYSTEM_QA },
      { role: "user", content: `Passage:\n\n${passage}` },
    ],
    temperature: 0.5,
  });

  const parsed = parseJson<{ question?: string; correct_answer?: string }>(raw);
  const question = parsed.question?.trim();
  const correctAnswer = parsed.correct_answer?.trim();
  if (!question || !correctAnswer) {
    throw new Error("Model returned invalid question/correct_answer");
  }
  return { question, correctAnswer };
}

export async function compareAnswer(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  question: string;
  correctAnswer: string;
  userAnswer: string;
}): Promise<string> {
  const { baseUrl, apiKey, model, question, correctAnswer, userAnswer } = params;
  return chatCompletion({
    baseUrl,
    apiKey,
    model,
    messages: [
      { role: "system", content: SYSTEM_DIALOG },
      {
        role: "user",
        content: `Question: ${question}\n\nModel answer: ${correctAnswer}\n\nLearner answer: ${userAnswer}`,
      },
    ],
    temperature: 0.4,
  });
}

export function initialSessionState(chapterText: string): SessionState {
  return {
    chapterText,
    cursor: 0,
    phase: { kind: "loading_block" },
    currentBlock: null,
    currentQA: null,
  };
}
