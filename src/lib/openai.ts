export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };

function normalizeBaseUrl(base: string): string {
  const trimmed = base.trim().replace(/\/+$/, "");
  return trimmed;
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
