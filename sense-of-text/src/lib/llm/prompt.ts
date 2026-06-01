import type { Token } from "@/lib/tokenize";

export function buildLlmMessages(text: string, tokens: Token[]) {
  const tokenList = tokens
    .map((t, i) => `${i}: ${JSON.stringify(t.text)}`)
    .join("\n");

  const system = `You evaluate how much each token contributes to the overall meaning of a text.
For each token index, assign importance from 0 to 1 (higher = removing that token would change the meaning more).
Respond with JSON only, no markdown, using this exact shape:
{"scores":[{"index":0,"importance":0.5},...]}
Include one entry per token index listed. importance must be between 0 and 1.`;

  const user = `Full text:
${text}

Tokens (index: text):
${tokenList}

Return JSON scores for every index.`;

  return [
    { role: "system" as const, content: system },
    { role: "user" as const, content: user },
  ];
}
