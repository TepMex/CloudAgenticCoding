import { chatCompletion } from "./openai";
import { parseJson } from "./json";

export type QAPair = { question: string; correctAnswer: string };

export type SessionPhase =
  | { kind: "loading_block" }
  | { kind: "loading_qa" }
  | { kind: "llm_question"; question: string }
  | { kind: "awaiting_user" }
  | { kind: "loading_feedback" }
  | {
      kind: "show_passage";
      passage: string;
      /** Sense-based analysis tied to the book passage (what aligns, what does not) */
      passageGroundedAnalysis: string;
      /** Optional: model’s own view, only when user enabled it in settings */
      llmOpinion: string | null;
    }
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

export function initialSessionState(chapterText: string): SessionState {
  return {
    chapterText,
    cursor: 0,
    phase: { kind: "loading_block" },
    currentBlock: null,
    currentQA: null,
  };
}
