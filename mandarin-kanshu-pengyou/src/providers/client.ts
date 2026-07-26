import type { ProviderCapabilities, ProviderProfile, RequestUsageRecord } from "../shared/domain";
import { createId, now } from "../shared/id";
import { safeErrorMessage } from "../security/redact";
import { db } from "../db/database";
import { getApiKey } from "./secrets";

export type ChatMessage = {
  role: "system" | "user" | "assistant";
  content: string;
};

export type ChatRequest = {
  profile: ProviderProfile;
  messages: ChatMessage[];
  signal?: AbortSignal;
  jsonMode?: boolean;
  task: string;
  bookId?: string;
};

export type ChatResponse = {
  content: string;
  usage?: {
    promptTokens?: number;
    completionTokens?: number;
    totalTokens?: number;
  };
  raw: unknown;
};

function completionsUrl(profile: ProviderProfile): string {
  const base = profile.baseUrl.replace(/\/$/, "");
  const path = profile.advanced.chatCompletionsPath ?? "/v1/chat/completions";
  if (path.startsWith("http")) return path;
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

export async function chatCompletion(req: ChatRequest): Promise<ChatResponse> {
  const apiKey = await getApiKey(req.profile.apiKeyReference);
  if (!apiKey) throw new Error("API key not set for this profile");

  const body: Record<string, unknown> = {
    model: req.profile.model,
    messages: req.messages,
    temperature: req.profile.advanced.temperature ?? 0.3,
  };
  if (req.profile.advanced.maxOutputTokens) {
    body.max_tokens = req.profile.advanced.maxOutputTokens;
  }
  if (req.jsonMode) {
    body.response_format = { type: "json_object" };
  }

  let response: Response;
  try {
    response = await fetch(completionsUrl(req.profile), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify(body),
      signal: req.signal,
    });
  } catch (e) {
    if (req.signal?.aborted) throw e;
    const msg = safeErrorMessage(e);
    if (/failed to fetch|networkerror|cors/i.test(msg)) {
      throw new Error(
        `CORS or network failure contacting provider. The endpoint must allow browser requests. (${msg})`,
      );
    }
    throw new Error(msg);
  }

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Provider HTTP ${response.status}: ${safeErrorMessage(text.slice(0, 400))}`);
  }

  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error("Provider returned non-JSON body");
  }

  const obj = json as {
    choices?: { message?: { content?: string } }[];
    usage?: {
      prompt_tokens?: number;
      completion_tokens?: number;
      total_tokens?: number;
    };
  };
  const content = obj.choices?.[0]?.message?.content ?? "";
  const usage = obj.usage
    ? {
        promptTokens: obj.usage.prompt_tokens,
        completionTokens: obj.usage.completion_tokens,
        totalTokens: obj.usage.total_tokens,
      }
    : undefined;

  const usageRecord: RequestUsageRecord = {
    id: createId("usage"),
    profileId: req.profile.id,
    task: req.task,
    promptTokens: usage?.promptTokens,
    completionTokens: usage?.completionTokens,
    totalTokens: usage?.totalTokens,
    usageAvailable: Boolean(usage),
    createdAt: now(),
    bookId: req.bookId,
  };
  void db.requestUsage.put(usageRecord);

  return { content, usage, raw: json };
}

export async function testProviderConnection(
  profile: ProviderProfile,
  signal?: AbortSignal,
): Promise<ProviderCapabilities> {
  const result: ProviderCapabilities = {
    corsOk: false,
    chatCompletionsOk: false,
    authOk: false,
    textCompletionOk: false,
    structuredOutputOk: false,
    jsonTextOk: false,
    cancellationOk: null,
    tokenUsageAvailable: false,
    testedAt: now(),
  };

  try {
    const basic = await chatCompletion({
      profile,
      task: "capability_test",
      signal,
      messages: [
        { role: "system", content: "Reply with exactly: pong" },
        { role: "user", content: "ping" },
      ],
    });
    result.corsOk = true;
    result.chatCompletionsOk = true;
    result.authOk = true;
    result.textCompletionOk = /pong/i.test(basic.content);
    result.tokenUsageAvailable = Boolean(basic.usage);

    try {
      const structured = await chatCompletion({
        profile,
        task: "capability_json_test",
        signal,
        jsonMode: true,
        messages: [
          {
            role: "system",
            content: 'Reply with JSON only: {"ok":true}',
          },
          { role: "user", content: "ok?" },
        ],
      });
      result.structuredOutputOk = /"ok"\s*:\s*true/.test(structured.content);
      result.jsonTextOk = result.structuredOutputOk || /\{[\s\S]*\}/.test(structured.content);
    } catch {
      // Try plain JSON without response_format
      const plain = await chatCompletion({
        profile,
        task: "capability_json_plain",
        signal,
        messages: [
          { role: "system", content: 'Reply with JSON only: {"ok":true}' },
          { role: "user", content: "ok?" },
        ],
      });
      result.structuredOutputOk = false;
      result.jsonTextOk = /"ok"\s*:\s*true/.test(plain.content);
    }

    // Cancellation probe (best-effort)
    try {
      const ctrl = new AbortController();
      const p = chatCompletion({
        profile,
        task: "capability_cancel",
        signal: ctrl.signal,
        messages: [
          { role: "user", content: "Write a long paragraph about tea." },
        ],
      });
      ctrl.abort();
      await p;
      result.cancellationOk = false;
    } catch (e) {
      result.cancellationOk =
        (e instanceof DOMException && e.name === "AbortError") ||
        (e instanceof Error && /abort/i.test(e.message));
    }
  } catch (e) {
    result.lastError = safeErrorMessage(e);
    if (/cors|failed to fetch|network/i.test(result.lastError)) {
      result.corsOk = false;
    }
  }

  await db.providerProfiles.update(profile.id, { capabilities: result });
  return result;
}
