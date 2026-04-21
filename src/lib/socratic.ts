import { chatCompletion, chatCompletionStream } from "./openai";
import { parseJson } from "./json";

export type QAPair = { question: string; correctAnswer: string };

export type SessionPhase =
  | { kind: "loading_block"; splitCharsReceived?: number; splitFirstBlockClosed?: boolean }
  | { kind: "loading_qa" }
  | { kind: "llm_question"; question: string }
  | { kind: "awaiting_user" }
  | { kind: "loading_feedback" }
  | {
      kind: "show_passage";
      passage: string;
      passageGroundedAnalysis: string;
      llmOpinion: string | null;
    }
  | { kind: "chapter_done" }
  | { kind: "error"; message: string };

export type SessionPersistence = {
  bookId: string;
  chapterId: string;
  chunkIndex: number;
};

export type SessionState = {
  chapterText: string;
  cursor: number;
  phase: SessionPhase;
  currentBlock: string | null;
  currentQA: QAPair | null;
  pendingNextCursor?: number;
  persistence: SessionPersistence | null;
};

const BLOCK_END = "[BLOCK_END]";

const SPLITTER_SYSTEM =
  "You are a text analysis assistant. Output only the processed text with [BLOCK_END] markers. No commentary or labels before or after.";

const SPLITTER_USER_PREFIX = `You are analyzing a textbook chapter excerpt. Split it into logical sections where each section represents one complete idea or subtopic.

Rules:
- Read the text carefully and decide where one logical block ends and another begins
- After completing each logical block, output the block's text followed immediately by ${BLOCK_END}
- Do NOT add any commentary, just the original text with markers
- Each block should be meaningful enough to generate 2-3 questions about
- Blocks are typically 3-8 paragraphs, but can vary based on content
- Preserve the original wording; copy the excerpt faithfully inside each block

Now process this excerpt:

`;

const SYSTEM_QA = `You are **Socratus**, a Socratic reading guide. You see the **upcoming passage** the learner will read only **after** they answer.

Your task: write **one** question and a concise model answer.

**Critical rules for the question**
- The question must be **motivated by** what this passage is centrally about (its tension, theme, or line of thought), but the learner has **not** read the passage yet.
- It must be **answerable without reading** the passage: a thoughtful person can respond using general knowledge, common sense, and careful reasoning alone.
- **Do not** assume or require knowledge of names, events, lines, or details that appear **only** in this excerpt. Do not ask "what does the text say," "according to the passage," "in this chapter," or similar.
- **Do** frame issues in generic or widely relatable terms when the passage is specific (e.g. tradeoffs between safety and freedom, obligations to others, how we justify beliefs), so the learner can engage sincerely before they read.

**Model answer**
- \`correct_answer\` is your concise reference for what a strong answer might emphasize; it may draw on the passage’s main idea because you have the text. It is **not** shown to the learner before they read.

Respond with JSON only: {"question": string, "correct_answer": string}`;

function buildFeedbackSystemPrompt(includeLlmOpinion: boolean): string {
  const core = `You are **Socratus**, a rigorous reading tutor. The learner answered a **pre-reading** question: they had **not** yet seen the passage (you have the passage below).

Your job is a **deep analytical response by sense** (not a phrase-by-phrase diff):
- Ground everything in the **BOOK PASSAGE** below: where the learner’s answer is **in accordance** with what the text supports (meaning, implications, tone), and where it is **not** supported, contradicted, or too vague to tie to the text.
- Discuss reasoning quality: inference vs. literal claim, missing nuance, overreach, or alternative valid readings **only insofar as the passage allows**. Do **not** fault the learner for lacking details they could not know before reading.
- You may use the reference answer only as a loose guide to the intended focus; prioritize fidelity to the **passage**.
- Write in clear prose (several short paragraphs). Do **not** quote long stretches of the passage; paraphrase or point briefly.
- Tone: constructive and precise. No bullet list titled "diff".`;

  if (!includeLlmOpinion) {
    return `${core}

Respond with **JSON only**:
{"passage_grounded_analysis": "<string>"}

Do not include any other keys.`;
  }

  return `${core}

Also produce **llm_opinion**: Socratus’s **personal or pedagogical view** on the question and answers—interpretive angles, what you find most interesting, or how you would frame the idea—**clearly separate** from the passage-grounded analysis. Label this mentally as opinion: it may go slightly beyond strict textual evidence.

Respond with **JSON only**:
{"passage_grounded_analysis": "<string>", "llm_opinion": "<string>"}`;
}

/** Characters of chapter text sent to the splitter per call (window for very long chapters). */
const MAX_EXCERPT_CHARS = 48_000;

export function processStreamBuffer(buffer: string): { completeBlocks: string[]; remainingBuffer: string } {
  const completeBlocks: string[] = [];
  let remainingBuffer = buffer;
  let markerIndex: number;
  while ((markerIndex = remainingBuffer.indexOf(BLOCK_END)) !== -1) {
    const blockText = remainingBuffer.slice(0, markerIndex).trim();
    if (blockText) completeBlocks.push(blockText);
    remainingBuffer = remainingBuffer.slice(markerIndex + BLOCK_END.length);
  }
  return { completeBlocks, remainingBuffer };
}

export function fallbackSplit(text: string, targetBlockSize = 2000): string[] {
  const paragraphs = text.split(/\n\n+/);
  const blocks: string[] = [];
  let currentBlock = "";
  for (const para of paragraphs) {
    const next = currentBlock ? `${currentBlock}\n\n${para}` : para;
    if (next.length > targetBlockSize && currentBlock) {
      blocks.push(currentBlock);
      currentBlock = para;
    } else {
      currentBlock = next;
    }
  }
  if (currentBlock.trim()) blocks.push(currentBlock.trim());
  return blocks;
}

function findBlockSpanInExcerpt(excerpt: string, modelBlock: string, searchFrom: number): { start: number; end: number } | null {
  const raw = modelBlock.trim();
  if (!raw) return null;

  let start = excerpt.indexOf(raw, searchFrom);
  if (start !== -1) {
    return { start, end: start + raw.length };
  }

  const headLen = Math.min(200, raw.length);
  const head = raw.slice(0, headLen);
  start = excerpt.indexOf(head, searchFrom);
  if (start !== -1) {
    const end = Math.min(excerpt.length, start + raw.length);
    return { start, end };
  }

  return null;
}

function mapModelBlockToChapterRange(
  chapterText: string,
  cursor: number,
  excerpt: string,
  modelBlock: string,
): { block: string; nextCursor: number } {
  const span = findBlockSpanInExcerpt(excerpt, modelBlock, 0);
  if (span) {
    const block = chapterText.slice(cursor + span.start, cursor + span.end).trim();
    if (block) {
      return { block, nextCursor: cursor + span.end };
    }
  }

  const fb = fallbackSplit(excerpt, 2000)[0]?.trim();
  if (fb) {
    const idx = excerpt.indexOf(fb);
    if (idx !== -1) {
      return {
        block: chapterText.slice(cursor + idx, cursor + idx + fb.length).trim(),
        nextCursor: cursor + idx + fb.length,
      };
    }
    return { block: fb, nextCursor: cursor + Math.min(fb.length, excerpt.length) };
  }

  return { block: excerpt.trim(), nextCursor: cursor + excerpt.length };
}

export type ChapterSplitProgress = {
  /** Delta text received from the model (including markers) for the current split call */
  charsReceived: number;
  /** True once the first [BLOCK_END] closed */
  firstBlockClosed: boolean;
};

/**
 * Streams the splitter model until the first complete logical block (marker) or the stream ends,
 * then maps that block to a range in `chapterText` so persistence stays aligned with the book.
 */
export async function streamNextLogicalBlock(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  chapterText: string;
  cursor: number;
  onProgress?: (p: ChapterSplitProgress) => void;
}): Promise<{ block: string; nextCursor: number }> {
  const { baseUrl, apiKey, model, chapterText, cursor, onProgress } = params;

  if (cursor >= chapterText.length) {
    return { block: "", nextCursor: cursor };
  }

  const excerpt = chapterText.slice(cursor, cursor + MAX_EXCERPT_CHARS);
  if (!excerpt.trim()) {
    return { block: "", nextCursor: chapterText.length };
  }

  const controller = new AbortController();
  let buffer = "";
  let firstRaw: string | null = null;
  let received = 0;

  try {
    for await (const piece of chatCompletionStream({
      baseUrl,
      apiKey,
      model,
      messages: [
        { role: "system", content: SPLITTER_SYSTEM },
        { role: "user", content: SPLITTER_USER_PREFIX + excerpt },
      ],
      temperature: 0.3,
      signal: controller.signal,
    })) {
      received += piece.length;
      buffer += piece;
      const { completeBlocks, remainingBuffer } = processStreamBuffer(buffer);
      buffer = remainingBuffer;
      onProgress?.({ charsReceived: received, firstBlockClosed: false });
      if (completeBlocks.length > 0) {
        firstRaw = completeBlocks[0] ?? null;
        controller.abort();
        onProgress?.({ charsReceived: received, firstBlockClosed: true });
        break;
      }
    }
  } catch (e) {
    const aborted =
      (e instanceof DOMException && e.name === "AbortError") ||
      (e instanceof Error && e.name === "AbortError");
    if (!aborted) {
      throw e;
    }
  }

  if (firstRaw === null) {
    const { completeBlocks, remainingBuffer } = processStreamBuffer(buffer);
    if (completeBlocks.length > 0) {
      firstRaw = completeBlocks[0] ?? null;
      buffer = remainingBuffer;
    }
  }

  let rawBlock = firstRaw?.trim() ?? buffer.trim();
  if (!rawBlock) {
    const fb = fallbackSplit(excerpt, 2000);
    rawBlock = fb[0] ?? excerpt.slice(0, Math.min(2000, excerpt.length));
  }

  const { block, nextCursor } = mapModelBlockToChapterRange(chapterText, cursor, excerpt, rawBlock);
  if (!block.trim()) {
    throw new Error("Empty logical block from model");
  }

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
      {
        role: "user",
        content: `Upcoming passage (learner does NOT see this until after they answer—write the question accordingly):\n\n${passage}`,
      },
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

export type AnswerFeedback = {
  passageGroundedAnalysis: string;
  llmOpinion: string | null;
};

export async function compareAnswer(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  question: string;
  correctAnswer: string;
  userAnswer: string;
  passage: string;
  includeLlmOpinion: boolean;
}): Promise<AnswerFeedback> {
  const { baseUrl, apiKey, model, question, correctAnswer, userAnswer, passage, includeLlmOpinion } = params;

  const raw = await chatCompletion({
    baseUrl,
    apiKey,
    model,
    messages: [
      { role: "system", content: buildFeedbackSystemPrompt(includeLlmOpinion) },
      {
        role: "user",
        content: `BOOK PASSAGE (ground truth for textual alignment):\n\n${passage}\n\n---\n\nQuestion: ${question}\n\nReference answer (guide only): ${correctAnswer}\n\nLearner answer: ${userAnswer}`,
      },
    ],
    temperature: 0.45,
  });

  const parsed = parseJson<{
    passage_grounded_analysis?: string;
    llm_opinion?: string;
  }>(raw);

  const passageGroundedAnalysis = parsed.passage_grounded_analysis?.trim();
  if (!passageGroundedAnalysis) {
    throw new Error("Model returned invalid passage_grounded_analysis");
  }

  const opinionRaw = parsed.llm_opinion?.trim();
  const llmOpinion = includeLlmOpinion ? (opinionRaw || null) : null;

  return { passageGroundedAnalysis, llmOpinion };
}

export function initialSessionState(
  chapterText: string,
  persistence: SessionPersistence | null = null,
): SessionState {
  return {
    chapterText,
    cursor: 0,
    phase: { kind: "loading_block" },
    currentBlock: null,
    currentQA: null,
    persistence,
  };
}
