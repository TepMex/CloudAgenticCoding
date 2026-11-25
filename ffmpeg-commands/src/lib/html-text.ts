const NAMED: Record<string, string> = {
  amp: "&",
  lt: "<",
  gt: ">",
  quot: '"',
  apos: "'",
  nbsp: " ",
  rsquo: "'",
  lsquo: "'",
  rdquo: '"',
  ldquo: '"',
  ndash: "-",
  mdash: "-",
  hellip: "...",
  times: "x",
  minus: "-",
};

export function decodeEntities(value: string): string {
  return value.replace(/&(#x?[0-9a-fA-F]+|[a-zA-Z]+);/g, (all, body: string) => {
    if (body.startsWith("#")) {
      const code =
        body[1] === "x" || body[1] === "X"
          ? Number.parseInt(body.slice(2), 16)
          : Number.parseInt(body.slice(1), 10);
      if (!Number.isFinite(code)) return all;
      try {
        return String.fromCodePoint(code);
      } catch {
        return all;
      }
    }
    return NAMED[body] ?? all;
  });
}

export function htmlToText(html: string): string {
  const stripped = html
    .replace(/<script[\s\S]*?<\/script>/gi, "")
    .replace(/<style[\s\S]*?<\/style>/gi, "")
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(p|div|li|dt|dd|h\d|tr|pre|blockquote|table)>/gi, "\n")
    .replace(/<[^>]+>/g, "");
  const decoded = decodeEntities(stripped).replace(/\u00a0/g, " ");
  return decoded
    .split("\n")
    .map(line => line.replace(/[ \t]+/g, " ").trim())
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export function clip(value: string, max: number): string {
  const text = value.replace(/\s+/g, " ").trim();
  if (text.length <= max) return text;
  return `${text.slice(0, max - 1).trimEnd()}…`;
}

export function cleanHeading(value: string): string {
  return value
    .replace(/#+\s*TOC\b/gi, " ")
    .replace(/#/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function bareTitle(title: string): string {
  return cleanHeading(title).replace(/^\d+(?:\.\d+)*\s+/, "").trim();
}

export function slug(value: string): string {
  const slugText = value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
  return slugText || "section";
}

export function shortHash(value: string): string {
  let hash = 2166136261;
  for (let i = 0; i < value.length; i++) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(36);
}

export function joinContinuations(text: string): string[] {
  const logical: string[] = [];
  let buffer = "";
  for (const raw of text.split("\n")) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) {
      if (!line && buffer) {
        logical.push(buffer.trim());
        buffer = "";
      }
      continue;
    }
    const continued = line.endsWith("\\");
    const piece = continued ? line.slice(0, -1).trim() : line;
    buffer = buffer ? `${buffer} ${piece}` : piece;
    if (!continued) {
      logical.push(buffer.trim());
      buffer = "";
    }
  }
  if (buffer) logical.push(buffer.trim());
  return logical.map(line => line.replace(/\s+/g, " ").trim()).filter(Boolean);
}

export function commandsFromPre(preInnerHtml: string): string[] {
  const logical = joinContinuations(htmlToText(preInnerHtml));
  const commands: string[] = [];
  for (const line of logical) {
    if (!/^(ffmpeg|ffprobe|ffplay)\b/.test(line)) continue;
    if (line.length > 900) continue;
    if (line.split(/\s+/).length < 3) continue;
    commands.push(line);
  }
  return commands;
}
