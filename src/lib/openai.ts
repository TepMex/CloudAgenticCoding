export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

function normalizeBaseUrl(base: string): string {
  const trimmed = base.trim().replace(/\/+$/, "");
  return trimmed;
}

/** Accumulate SSE lines and yield JSON payloads from `data: {...}` chunks. */
function* parseSseDataLines(chunkText: string, lineBuffer: { value: string }): Generator<string> {
  lineBuffer.value += chunkText;
  const lines = lineBuffer.value.split("\n");
  lineBuffer.value = lines.pop() ?? "";
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed.startsWith("data:")) continue;
    const payload = trimmed.slice("data:".length).trim();
    if (payload === "[DONE]") continue;
    if (!payload) continue;
    yield payload;
  }
}

export async function* chatCompletionStream(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature?: number;
  signal?: AbortSignal;
}): AsyncGenerator<string, void, unknown> {
  const { baseUrl, apiKey, model, messages, temperature = 0.4, signal } = params;
  const root = normalizeBaseUrl(baseUrl);
  const url = `${root}/v1/chat/completions`;

  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      messages,
      temperature,
      stream: true,
    }),
    signal,
  });

  if (!res.ok) {
    const raw = await res.text();
    throw new Error(`API error ${res.status}: ${raw.slice(0, 500)}`);
  }

  const reader = res.body?.getReader();
  if (!reader) {
    throw new Error("Response has no readable body");
  }

  const decoder = new TextDecoder();
  const lineBuffer = { value: "" };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const piece = decoder.decode(value, { stream: true });
      for (const payload of parseSseDataLines(piece, lineBuffer)) {
        let data: unknown;
        try {
          data = JSON.parse(payload) as unknown;
        } catch {
          continue;
        }
        const obj = data as {
          choices?: Array<{ delta?: { content?: string | null }; finish_reason?: string | null }>;
          error?: { message?: string };
        };
        if (obj.error?.message) {
          throw new Error(obj.error.message);
        }
        const content = obj.choices?.[0]?.delta?.content;
        if (typeof content === "string" && content.length > 0) {
          yield content;
        }
      }
    }
    for (const payload of parseSseDataLines("\n", lineBuffer)) {
      let data: unknown;
      try {
        data = JSON.parse(payload) as unknown;
      } catch {
        continue;
      }
      const obj = data as {
        choices?: Array<{ delta?: { content?: string | null } }>;
        error?: { message?: string };
      };
      if (obj.error?.message) throw new Error(obj.error.message);
      const content = obj.choices?.[0]?.delta?.content;
      if (typeof content === "string" && content.length > 0) yield content;
    }
  } finally {
    reader.releaseLock();
  }
}

export async function chatCompletion(params: {
  baseUrl: string;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  temperature?: number;
}): Promise<string> {
  const { baseUrl, apiKey, model, messages, temperature = 0.4 } = params;
  const root = normalizeBaseUrl(baseUrl);
  const url = `${root}/v1/chat/completions`;

  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${apiKey}`,
    },
    body: JSON.stringify({
      model,
      messages,
      temperature,
    }),
  });

  const raw = await res.text();
  if (!res.ok) {
    throw new Error(`API error ${res.status}: ${raw.slice(0, 500)}`);
  }

  let data: unknown;
  try {
    data = JSON.parse(raw) as unknown;
  } catch {
    throw new Error("Invalid JSON from API");
  }

  const obj = data as {
    choices?: Array<{ message?: { content?: string | null } }>;
    error?: { message?: string };
  };

  if (obj.error?.message) {
    throw new Error(obj.error.message);
  }

  const content = obj.choices?.[0]?.message?.content;
  if (typeof content !== "string") {
    throw new Error("Unexpected API response shape (missing choices[0].message.content)");
  }

  return content;
}
