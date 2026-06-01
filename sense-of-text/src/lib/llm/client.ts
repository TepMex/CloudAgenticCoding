import { normalizeApiToken, validateApiToken } from "@/lib/api-token";
import type { AppSettings } from "@/lib/settings";
import type { Token } from "@/lib/tokenize";
import { buildLlmMessages } from "./prompt";
import { alignScoresToTokens, parseLlmScores } from "./parse";

type ChatCompletionResponse = {
  choices?: Array<{
    message?: { content?: string };
  }>;
  error?: { message?: string };
};

export async function analyzeWithLlm(
  text: string,
  tokens: Token[],
  model: string,
  settings: AppSettings,
  signal?: AbortSignal,
): Promise<number[]> {
  const tokenError = validateApiToken(settings.token);
  if (tokenError) {
    throw new Error(tokenError);
  }
  if (!settings.baseUrl.trim()) {
    throw new Error("Base URL is required for LLM analysis.");
  }

  const messages = buildLlmMessages(text, tokens);
  const token = normalizeApiToken(settings.token);

  const res = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      baseUrl: settings.baseUrl.replace(/\/$/, ""),
      token,
      model,
      messages,
      responseFormat: { type: "json_object" },
    }),
    signal,
  });

  const data = (await res.json()) as ChatCompletionResponse & { message?: string };

  if (!res.ok) {
    throw new Error(data.error?.message ?? data.message ?? `API error ${res.status}`);
  }

  const content = data.choices?.[0]?.message?.content;
  if (!content) {
    throw new Error("Empty response from LLM");
  }

  const scores = parseLlmScores(content, tokens.length);
  return alignScoresToTokens(scores, tokens);
}
