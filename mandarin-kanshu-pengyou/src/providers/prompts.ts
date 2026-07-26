import { SECURITY_PREAMBLE, quoteUntrusted } from "../providers/untrusted";

export function simplificationSystemPrompt(level: 1 | 2 | 3, hskLevel: number): string {
  const levelGuide =
    level === 1
      ? `Level 1 — Easier rewrite:
- Preserve main meaning and important details.
- Use shorter sentences.
- Prefer vocabulary below HSK ${hskLevel}.
- Preserve names and fictional terms.
- Avoid unnecessary grammar explanations.`
      : level === 2
        ? `Level 2 — Explicit rewrite:
- Make implicit relationships explicit.
- Replace difficult ordinary vocabulary with simpler vocabulary.
- Clarify omitted subjects when context supports it.
- Break long clauses into short sentences.
- Preserve names and fictional terms.`
        : `Level 3 — Minimal meaning:
- State the central event, intention, or relationship.
- Use very basic Chinese.
- Permit loss of literary style and secondary details.
- Do NOT translate into the learner's native language.`;

  return `${SECURITY_PREAMBLE}

You rewrite Chinese fiction passages into simpler Chinese. This is NOT a translation and NOT primarily a grammar lesson.

${levelGuide}

Fictional terminology:
- Mark book-specific terms with ◆ on first appearance only in your response.
- Format: 术语◆（简短说明）
- If uncertain, say so in the parentheses. If unknown, keep the term and say meaning is not yet clear.
- Do not replace important fictional terms with generic words.

Output JSON:
{
  "simplifiedChinese": string,
  "terminologyNotes": string[],
  "uncertaintyNotes": string[]
}`;
}

export function simplificationUserPrompt(input: {
  originalPassage: string;
  contextBefore: string;
  contextAfter: string;
  bookMemoryCompact: string;
  chapterSummary: string;
  recentEvents: string;
  sourceTextForIteration?: string;
}): string {
  return [
    quoteUntrusted("Original passage", input.originalPassage),
    quoteUntrusted("Two paragraphs before", input.contextBefore || "(none)"),
    quoteUntrusted("Two paragraphs after", input.contextAfter || "(none)"),
    quoteUntrusted("Compact book memory", input.bookMemoryCompact || "(empty)"),
    quoteUntrusted("Current chapter summary", input.chapterSummary || "(none)"),
    quoteUntrusted("Recent chapter events", input.recentEvents || "(none)"),
    input.sourceTextForIteration
      ? quoteUntrusted("Text to simplify further (from prior level)", input.sourceTextForIteration)
      : "Simplify the original passage.",
  ].join("\n\n");
}

export function assessmentSystemPrompt(learnerLanguage: string): string {
  return `${SECURITY_PREAMBLE}

You assess a learner's understanding of an original Chinese passage.
The learner answers in ${learnerLanguage}.

Do NOT evaluate:
- grammar, style, vocabulary richness, or spelling in ${learnerLanguage}
  (unless spelling makes meaning impossible to infer).

Scoring (0–4):
0 Missed — fundamentally misunderstood
1 Emerging — isolated details, missed central event
2 Main idea — understood main event/claim
3 Strong — main meaning + important relationships/details
4 Deep — also tone, implication, or narrative purpose

Priorities: core meaning required; details improve score; tone is mastery bonus.
When multiple interpretations are plausible, accept them and note ambiguity.
Ask at most one next follow-up question in simple Chinese when shouldContinueQuestioning is true.
Do not reveal the full answer in the question. Provide a brief keyClueInChinese grounding cue.

Output JSON matching the UnderstandingAssessment schema fields.`;
}

export function assessmentUserPrompt(input: {
  passage: string;
  contextBefore: string;
  contextAfter: string;
  bookMemoryCompact: string;
  learnerAnswer: string;
  learnerLanguage: string;
  questionIndex: number;
  priorQAs: string;
}): string {
  return [
    `Learner language: ${input.learnerLanguage}`,
    `Question index (0 = initial free answer): ${input.questionIndex}`,
    quoteUntrusted("Original Chinese passage", input.passage),
    quoteUntrusted("Context before", input.contextBefore || "(none)"),
    quoteUntrusted("Context after", input.contextAfter || "(none)"),
    quoteUntrusted("Compact book memory", input.bookMemoryCompact || "(empty)"),
    quoteUntrusted("Prior Q&A", input.priorQAs || "(none)"),
    quoteUntrusted("Learner answer", input.learnerAnswer),
  ].join("\n\n");
}

export function memoryPatchSystemPrompt(): string {
  return `${SECURITY_PREAMBLE}

You maintain CANONICAL book memory for a Chinese novel.
Only extract facts supported by the supplied Chinese text and existing memory.
Distinguish confidence: certain | probable | uncertain.
Do not use learner answers, simplifications, or companion speculation.
Do not invent future plot. Do not store emotional predictions as facts.

Output JSON memory patch:
{
  "synopsisUpdate": string|null,
  "entities": [{ "op":"upsert"|"remove", "id"?, "type"?, "canonicalName"?, "aliases"?, "description"?, "confidence"? }],
  "events": [{ "op":"add"|"remove", "id"?, "summary"?, "participants"?, "locationName"?, "confidence"? }],
  "unresolvedThreads": string[]
}`;
}

export function memoryPatchUserPrompt(input: {
  passage: string;
  context: string;
  existingMemory: string;
  chapterId: string;
}): string {
  return [
    `Chapter id: ${input.chapterId}`,
    quoteUntrusted("Passage", input.passage),
    quoteUntrusted("Nearby context", input.context),
    quoteUntrusted("Existing compact memory", input.existingMemory),
  ].join("\n\n");
}

export function initialMemorySystemPrompt(): string {
  return `${SECURITY_PREAMBLE}

Extract a lightweight initial book memory from early text only.
Output JSON:
{
  "synopsis": string,
  "entities": [{ "type", "canonicalName", "aliases", "description", "confidence" }],
  "unresolvedThreads": string[]
}`;
}

export function companionSystemPrompt(): string {
  return `${SECURITY_PREAMBLE}

You are a quiet reading companion reacting like a friend — not a grader or encyclopedia.
You may express emotion, surprise, suspicion, or speculation based ONLY on supplied text and memory.
Predictions must never be treated as facts and must not spoil future plot from pretrained knowledge.
Keep it brief and warm. No avatar. Output JSON:
{ "reaction": string, "speculation": string|null, "isPrediction": boolean }`;
}

export function repairSystemPrompt(): string {
  return `${SECURITY_PREAMBLE}

The previous model response was malformed. Repair it into valid JSON matching the expected schema.
Do not add new claims. Output JSON only — the repaired object itself (not wrapped).`;
}

export function chapterSummarySystemPrompt(): string {
  return `${SECURITY_PREAMBLE}

Summarize the completed chapter's events into one concise Chinese-or-learner-language-neutral factual summary.
List unresolved threads. Output JSON:
{ "summary": string, "unresolvedThreads": string[] }`;
}
