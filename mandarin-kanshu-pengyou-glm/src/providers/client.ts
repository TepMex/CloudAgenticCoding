import { z } from "zod";
import { redactKeys } from "../shared/util";
import { extractJson, validateWithSchema } from "./parse";
import { repairPrompt } from "./prompts";

export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };
export type ChatResult = { text: string; promptTokens: number | null; completionTokens: number | null; totalTokens: number | null; raw: string };

export type ProviderClientOptions = {
  baseUrl: string; apiKey: string; model: string;
  temperature?: number; maxOutputTokens?: number; chatCompletionsPath?: string;
  supportsJsonMode?: boolean; supportsStructuredOutput?: boolean; schemaName?: string; schemaJson?: string;
};

export class CancelledError extends Error { constructor() { super("Request cancelled"); this.name = "CancelledError"; } }
export class ProviderError extends Error { status?: number; constructor(message: string, status?: number) { super(message); this.name = "ProviderError"; this.status = status; } }

export class ProviderClient {
  private controller: AbortController | null = null;

  async chat(messages: ChatMessage[], opts: ProviderClientOptions, signal?: AbortSignal): Promise<ChatResult> {
    this.controller = new AbortController();
    if (signal) signal.addEventListener("abort", () => this.controller!.abort());
    const path = opts.chatCompletionsPath || "/v1/chat/completions";
    const url = joinUrl(opts.baseUrl, path);
    const body: Record<string, unknown> = { model: opts.model, messages, stream: false };
    if (opts.temperature !== undefined) body.temperature = opts.temperature;
    if (opts.maxOutputTokens !== undefined) body.max_tokens = opts.maxOutputTokens;
    if (opts.supportsStructuredOutput && opts.schemaJson) {
      body.response_format = { type: "json_schema", json_schema: { name: opts.schemaName || "schema", schema: JSON.parse(opts.schemaJson) } };
    } else if (opts.supportsJsonMode) {
      body.response_format = { type: "json_object" };
    }
    let res: Response;
    try {
      res = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json", Authorization: "Bearer " + opts.apiKey }, body: JSON.stringify(body), signal: this.controller.signal });
    } catch (e) {
      if (this.controller.signal.aborted) throw new CancelledError();
      if (e instanceof DOMException && e.name === "AbortError") throw new CancelledError();
      throw new ProviderError(redactKeys("Network error: " + String(e)));
    }
    if (this.controller.signal.aborted) throw new CancelledError();
    if (!res.ok) {
      const t = await res.text().catch(() => "");
      throw new ProviderError(redactKeys("Provider returned " + res.status + ": " + t.slice(0, 500)), res.status);
    }
    const json = await res.json().catch(() => null);
    if (!json) throw new ProviderError("Provider returned non-JSON response");
    const text = json?.choices?.[0]?.message?.content ?? "";
    const usage = json?.usage;
    return { text: typeof text === "string" ? text : JSON.stringify(text), promptTokens: usage?.prompt_tokens ?? null, completionTokens: usage?.completion_tokens ?? null, totalTokens: usage?.total_tokens ?? null, raw: typeof text === "string" ? text : JSON.stringify(text) };
  }

  cancel(): void { this.controller?.abort(); }

  async structured<T>(
    messages: ChatMessage[], opts: ProviderClientOptions, schema: z.ZodType<T>, schemaJson: string, repairClientOpts: ProviderClientOptions, signal?: AbortSignal
  ): Promise<{ data: T; usage: ChatResult | null } | { error: string; raw: string; usage: ChatResult | null }> {
    const result = await this.chat(messages, { ...opts, schemaJson }, signal);
    const parsed = extractJson(result.text);
    if (parsed !== null) {
      const v = validateWithSchema(parsed, schema);
      if (v.ok && v.data !== undefined) return { data: v.data, usage: result };
    }
    const repair = repairPrompt({ raw: result.text, schemaJson });
    const repairResult = await this.chat([{ role: "system", content: repair.system }, { role: "user", content: repair.user }], repairClientOpts, signal).catch(() => null);
    if (repairResult) {
      const reparsed = extractJson(repairResult.text);
      if (reparsed !== null) {
        const v = validateWithSchema(reparsed, schema);
        if (v.ok && v.data !== undefined) return { data: v.data, usage: result };
      }
    }
    return { error: "Malformed structured output", raw: result.text, usage: result };
  }
}

function joinUrl(base: string, path: string): string {
  if (/^https?:\/\//i.test(path)) return path;
  const b = base.replace(/\/+$/, "");
  const p = path.startsWith("/") ? path : "/" + path;
  return b + p;
}

export async function testConnection(opts: ProviderClientOptions): Promise<{ ok: boolean; supportsStructuredOutput: boolean; supportsJsonMode: boolean; supportsTokenUsage: boolean; notes: string }> {
  const client = new ProviderClient();
  try {
    const result = await client.chat([{ role: "system", content: "Return the single word: ok" }, { role: "user", content: "ping" }], { ...opts, maxOutputTokens: 10 }, undefined);
    const hasUsage = result.totalTokens !== null && result.totalTokens !== undefined;
    let supportsJsonMode = false;
    let supportsStructuredOutput = false;
    try {
      const jsonRes = await client.chat([{ role: "system", content: 'Return JSON: {"ok":true}' }, { role: "user", content: "go" }], { ...opts, supportsJsonMode: true, maxOutputTokens: 20 }, undefined);
      supportsJsonMode = /{/.test(jsonRes.text);
    } catch { supportsJsonMode = false; }
    try {
      const structRes = await client.chat([{ role: "system", content: "Return JSON matching the schema." }, { role: "user", content: "go" }], { ...opts, supportsStructuredOutput: true, schemaJson: JSON.stringify({ type: "object", properties: { ok: { type: "boolean" } }, required: ["ok"] }), maxOutputTokens: 20 }, undefined);
      supportsStructuredOutput = /"ok"\s*:\s*(true|false)/i.test(structRes.text);
    } catch { supportsStructuredOutput = false; }
    return { ok: true, supportsStructuredOutput, supportsJsonMode, supportsTokenUsage: hasUsage, notes: hasUsage ? "OK" : "OK (usage unavailable)" };
  } catch (e) {
    const msg = e instanceof Error ? redactKeys(e.message) : String(e);
    return { ok: false, supportsStructuredOutput: false, supportsJsonMode: false, supportsTokenUsage: false, notes: msg };
  }
}
