export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

/**
 * OpenAI-compatible Chat Completions URL.
 * Accepts a host (`https://api.openai.com`), a versioned root (`…/v1`),
 * or a full endpoint (`…/v1/chat/completions`).
 */
export function chatCompletionsUrl(baseUrl: string): string {
  const trimmed = baseUrl.trim();
  if (!trimmed) throw new Error("Укажите базовый URL.");
  let url: URL;
  try {
    url = new URL(trimmed);
  } catch {
    throw new Error("Базовый URL должен начинаться с http:// или https://.");
  }
  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new Error("Допустимы только http и https.");
  }
  let path = url.pathname.replace(/\/+$/, "");
  if (path.endsWith("/chat/completions")) {
    // already the full method path
  } else if (path.endsWith("/v1")) {
    path = `${path}/chat/completions`;
  } else {
    path = `${path}/v1/chat/completions`;
  }
  url.pathname = path;
  url.search = "";
  url.hash = "";
  return url.toString();
}

export function providerLabel(baseUrl: string): string {
  if (!baseUrl.trim()) return "Не задано";
  try {
    const host = new URL(baseUrl.trim()).host;
    if (host === "api.openai.com") return "OpenAI";
    return host || "Свой endpoint";
  } catch {
    return "Свой endpoint";
  }
}

function messageText(content: unknown): string | null {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return null;
  const parts = content
    .map(part => {
      if (typeof part === "string") return part;
      if (part && typeof part === "object" && "text" in part && typeof part.text === "string") return part.text;
      return "";
    })
    .join("");
  return parts.length > 0 ? parts : null;
}

async function postChat(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature?: number;
  signal?: AbortSignal;
}): Promise<string> {
  const url = chatCompletionsUrl(params.baseUrl);
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (params.apiKey.trim()) headers.Authorization = `Bearer ${params.apiKey.trim()}`;
  const body: Record<string, unknown> = {
    model: params.model,
    messages: params.messages,
  };
  if (params.temperature !== undefined) body.temperature = params.temperature;

  let res: Response;
  try {
    res = await fetch(url, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
      signal: params.signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "TimeoutError") {
      throw new Error("Время ожидания истекло. Проверьте адрес и сеть.");
    }
    const message = error instanceof Error ? error.message : "Сеть недоступна";
    throw new Error(`Не удалось связаться с endpoint: ${message}`);
  }

  const raw = await res.text();
  if (!res.ok) {
    throw new Error(`API ${res.status}: ${raw.slice(0, 400)}`);
  }

  let data: unknown;
  try {
    data = JSON.parse(raw) as unknown;
  } catch {
    throw new Error("Endpoint вернул не JSON.");
  }
  const obj = data as {
    choices?: Array<{ message?: { content?: unknown } }>;
    error?: { message?: string };
  };
  if (obj.error?.message) throw new Error(obj.error.message);
  const content = messageText(obj.choices?.[0]?.message?.content);
  if (content === null) throw new Error("В ответе нет choices[0].message.content.");
  return content;
}

export async function chatCompletion(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature?: number;
  signal?: AbortSignal;
}): Promise<string> {
  const temperature = params.temperature ?? 0.4;
  try {
    return await postChat({ ...params, temperature });
  } catch (error) {
    if (temperature !== undefined && /temperature/i.test(error instanceof Error ? error.message : "")) {
      return await postChat({ ...params, temperature: undefined });
    }
    throw error;
  }
}
