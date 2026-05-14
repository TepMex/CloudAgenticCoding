import { parseJson } from "@/lib/json";
import { chatCompletion, type ChatMessage } from "@/lib/openai";
import type { KoanSettings } from "@/lib/settings";

const FEEDBACK_SYSTEM = `You grade a Mandarin cloze task. You know the correct hidden word, the story with a blank (___), and the learner's guess.

Classify the guess into exactly one verdict:
- "correct": same word as the target, or trivial surface variant only (e.g. full-width vs half-width) that does not change meaning.
- "synonym": a different word that could fit the sentence in real usage but is not the same lexeme as the target; nuances matter.
- "absolute_mistake": wrong meaning, wrong part of speech for the slot, nonsense, unrelated word, or a word that does not plausibly fit.

Language:
- Write "feedback" in **Mandarin Chinese** (brief, clear). For "synonym", explain how the guess differs in nuance, register, or usage from the target.

Respond with **JSON only**:
{"verdict": "correct" | "absolute_mistake" | "synonym", "feedback": "<string>"}`;

export type ClozeVerdict = "correct" | "absolute_mistake" | "synonym";

export type StoryResult = { story: string };

export type FeedbackResult = { verdict: ClozeVerdict; feedback: string };

function fillStoryTemplate(template: string, vocab: string): string {
  return template.replace(/\{\{VOCAB\}\}/g, vocab);
}

export async function generateClozeStory(params: {
  settings: KoanSettings;
  targetVocab: string;
}): Promise<StoryResult> {
  const { settings, targetVocab } = params;
  const userContent = fillStoryTemplate(settings.storyPrompt, targetVocab);
  const messages: ChatMessage[] = [
    { role: "system", content: "You follow instructions and output JSON only." },
    { role: "user", content: userContent },
  ];
  const raw = await chatCompletion({
    baseUrl: settings.apiBaseUrl,
    apiKey: settings.apiKey,
    model: settings.model,
    messages,
    temperature: 0.65,
  });
  const parsed = parseJson<{ story?: string }>(raw);
  const story = typeof parsed.story === "string" ? parsed.story.trim() : "";
  if (!story || !story.includes("___")) {
    throw new Error('Model output must include a JSON "story" string containing ___');
  }
  return { story };
}

export async function evaluateClozeAnswer(params: {
  settings: KoanSettings;
  targetVocab: string;
  storyWithBlank: string;
  userAnswer: string;
}): Promise<FeedbackResult> {
  const { settings, targetVocab, storyWithBlank, userAnswer } = params;
  const userBlock = `Target word (correct answer): ${targetVocab}
Story with blank (___): ${storyWithBlank}
Learner guess: ${userAnswer.trim()}`;
  const messages: ChatMessage[] = [
    { role: "system", content: FEEDBACK_SYSTEM },
    { role: "user", content: userBlock },
  ];
  const raw = await chatCompletion({
    baseUrl: settings.apiBaseUrl,
    apiKey: settings.apiKey,
    model: settings.model,
    messages,
    temperature: 0.2,
  });
  const parsed = parseJson<{ verdict?: string; feedback?: string }>(raw);
  const verdictRaw = parsed.verdict;
  const feedback = typeof parsed.feedback === "string" ? parsed.feedback.trim() : "";
  const verdict =
    verdictRaw === "correct" || verdictRaw === "absolute_mistake" || verdictRaw === "synonym"
      ? verdictRaw
      : null;
  if (!verdict || !feedback) {
    throw new Error('Model output must include verdict ("correct" | "absolute_mistake" | "synonym") and feedback');
  }
  return { verdict, feedback };
}
