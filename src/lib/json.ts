export function extractJsonObject(text: string): string {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start === -1 || end === -1 || end <= start) {
    throw new Error("No JSON object found in model output");
  }
  return text.slice(start, end + 1);
}

export function parseJson<T>(text: string): T {
  const slice = extractJsonObject(text);
  return JSON.parse(slice) as T;
}
