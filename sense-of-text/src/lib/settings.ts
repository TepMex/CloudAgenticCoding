import { z } from "zod";

const STORAGE_KEY = "sense-of-text:settings";

export const AppSettingsSchema = z.object({
  baseUrl: z.string(),
  token: z.string(),
  embeddingModels: z.array(z.string()),
  llmModels: z.array(z.string()),
});

export type AppSettings = z.infer<typeof AppSettingsSchema>;

export const DEFAULT_SETTINGS: AppSettings = {
  baseUrl: "https://api.openai.com",
  token: "",
  embeddingModels: ["Xenova/all-MiniLM-L6-v2"],
  llmModels: ["gpt-4o-mini"],
};

export type ModelKind = "embedding" | "llm";

export function toModelOptionId(kind: ModelKind, model: string): string {
  return `${kind}:${model}`;
}

export function parseModelOptionId(
  id: string,
): { kind: ModelKind; model: string } | null {
  const sep = id.indexOf(":");
  if (sep <= 0) return null;
  const kind = id.slice(0, sep);
  if (kind !== "embedding" && kind !== "llm") return null;
  const model = id.slice(sep + 1);
  if (!model) return null;
  return { kind, model };
}

export function allModelOptions(settings: AppSettings): {
  id: string;
  kind: ModelKind;
  model: string;
}[] {
  return [
    ...settings.embeddingModels.map(model => ({
      id: toModelOptionId("embedding", model),
      kind: "embedding" as const,
      model,
    })),
    ...settings.llmModels.map(model => ({
      id: toModelOptionId("llm", model),
      kind: "llm" as const,
      model,
    })),
  ];
}

export function parseModelList(raw: string): string[] {
  return raw
    .split("\n")
    .map(line => line.trim())
    .filter(Boolean);
}

export function formatModelList(models: string[]): string {
  return models.join("\n");
}

function normalizeModelList(value: unknown): string[] | undefined {
  if (Array.isArray(value)) {
    return value
      .filter((item): item is string => typeof item === "string")
      .map(item => item.trim())
      .filter(Boolean);
  }
  if (typeof value === "string") {
    return parseModelList(value);
  }
  return undefined;
}

function migrateSettings(raw: unknown): AppSettings {
  if (!raw || typeof raw !== "object") return DEFAULT_SETTINGS;
  const data = raw as Record<string, unknown>;
  return {
    baseUrl:
      typeof data.baseUrl === "string" && data.baseUrl.trim()
        ? data.baseUrl.trim()
        : DEFAULT_SETTINGS.baseUrl,
    token: typeof data.token === "string" ? data.token : DEFAULT_SETTINGS.token,
    embeddingModels:
      normalizeModelList(data.embeddingModels) ?? DEFAULT_SETTINGS.embeddingModels,
    llmModels: normalizeModelList(data.llmModels) ?? DEFAULT_SETTINGS.llmModels,
  };
}

export function loadSettings(): AppSettings {
  if (typeof localStorage === "undefined") {
    return DEFAULT_SETTINGS;
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return DEFAULT_SETTINGS;
    const json: unknown = JSON.parse(raw);
    const parsed = AppSettingsSchema.safeParse(json);
    return parsed.success ? parsed.data : migrateSettings(json);
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export function saveSettings(settings: AppSettings): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
}

export function getModelKind(
  modelOrOptionId: string,
  settings: AppSettings,
): ModelKind | null {
  const fromOption = parseModelOptionId(modelOrOptionId);
  if (fromOption) return fromOption.kind;
  if (settings.embeddingModels.includes(modelOrOptionId)) return "embedding";
  if (settings.llmModels.includes(modelOrOptionId)) return "llm";
  return null;
}

export function resolveModelName(modelOrOptionId: string): string | null {
  const fromOption = parseModelOptionId(modelOrOptionId);
  return fromOption?.model ?? modelOrOptionId;
}

export function allModels(settings: AppSettings): string[] {
  return allModelOptions(settings).map(option => option.id);
}
