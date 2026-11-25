import type { DocPageId } from "./contract.ts";
import {
  buildEmbedText,
  canonicalCommand,
  componentName,
  familyLabel,
  optionExample,
  templateCommand,
} from "./embed-text.ts";
import { bareTitle, cleanHeading, clip, commandsFromPre, htmlToText, shortHash, slug } from "./html-text.ts";
import type { Chunk } from "./types.ts";

type Heading = {
  level: number;
  title: string;
  anchor: string;
  index: number;
  end: number;
};

const SKIP_TITLE = /^(examples?|commands?|syntax|description|options?|see also|authors|notes?|table of contents)$/i;

export function familyOf(chapter: string, page: DocPageId | string): string | null {
  const text = chapter.toLowerCase();
  if (/table of contents|see also|authors|synopsis/.test(text)) return null;
  if (page === "filters") {
    if (text.includes("audio filter")) return "audio-filter";
    if (text.includes("audio source")) return "audio-source";
    if (text.includes("audio sink")) return "audio-sink";
    if (text.includes("video source")) return "video-source";
    if (text.includes("video sink")) return "video-sink";
    if (text.includes("multimedia source")) return "multimedia-source";
    if (text.includes("multimedia")) return "multimedia-filter";
    if (text.includes("video")) return "video-filter";
    return null;
  }
  if (page === "codecs") {
    if (text.includes("subtitle") && text.includes("encoder")) return "subtitle-encoder";
    if (text.includes("subtitle") && text.includes("decoder")) return "subtitle-decoder";
    if (text.includes("video decoder")) return "video-decoder";
    if (text.includes("audio decoder")) return "audio-decoder";
    if (text.includes("video encoder")) return "video-encoder";
    if (text.includes("audio encoder")) return "audio-encoder";
    return null;
  }
  if (page === "formats") {
    if (text.includes("demux")) return "demuxer";
    if (text.includes("mux")) return "muxer";
    return null;
  }
  if (page === "protocols") return text.includes("protocol") && !text.includes("option") ? "protocol" : null;
  if (page === "bsf") return text.includes("bitstream") ? "bitstream-filter" : null;
  if (page === "devices") {
    if (text.includes("input device")) return "input-device";
    if (text.includes("output device")) return "output-device";
    return null;
  }
  if (page === "ffprobe") return text.includes("writer") ? "writer" : null;
  return null;
}

function findHeadings(html: string): Heading[] {
  const headings: Heading[] = [];
  const re = /<h([23])\b[^>]*>([\s\S]*?)<\/h\1>/gi;
  for (const match of html.matchAll(re)) {
    const raw = match[2] ?? "";
    const hrefs = [...raw.matchAll(/href="#([^"]+)"/g)]
      .map(item => item[1] ?? "")
      .filter(href => href && !href.startsWith("toc-"));
    const title = cleanHeading(htmlToText(raw));
    const index = match.index ?? 0;
    headings.push({
      level: Number(match[1]),
      title,
      anchor: hrefs[0] || slug(title),
      index,
      end: index + match[0].length,
    });
  }
  return headings;
}

function sectionEnd(headings: Heading[], index: number, htmlLength: number): number {
  const level = headings[index]?.level ?? 3;
  for (let cursor = index + 1; cursor < headings.length; cursor++) {
    const next = headings[cursor];
    if (next && next.level <= level) return next.index;
  }
  return htmlLength;
}

function chapterAt(headings: Heading[], position: number): Heading | undefined {
  let chapter: Heading | undefined;
  for (const heading of headings) {
    if (heading.index > position) break;
    if (heading.level === 2) chapter = heading;
  }
  return chapter;
}

function headingAt(headings: Heading[], position: number): Heading | undefined {
  let current: Heading | undefined;
  for (const heading of headings) {
    if (heading.index > position) break;
    current = heading;
  }
  return current;
}

function leadText(sectionHtml: string): string {
  const cut = sectionHtml.split(/<pre\b|<dl\b|<table\b|<h4\b/i)[0] ?? sectionHtml;
  return clip(htmlToText(cut), 420);
}

function optionSignature(dtHtml: string): string | null {
  const text = htmlToText(dtHtml).replace(/\s+/g, " ").trim();
  if (!text.startsWith("-")) return null;
  return clip(text, 180);
}

function flagOf(signature: string): string {
  const token = signature.split(/\s+/)[0] ?? signature;
  return token.replace(/,+$/, "");
}

function commandsIn(html: string): string[] {
  const commands: string[] = [];
  for (const match of html.matchAll(/<pre\b[^>]*>([\s\S]*?)<\/pre>/gi)) {
    commands.push(...commandsFromPre(match[1] ?? ""));
  }
  return [...new Set(commands)];
}

function precedingSummary(html: string, preStart: number, headingTitle: string): string {
  const windowHtml = html.slice(Math.max(0, preStart - 1800), preStart);
  const paragraphs = [...windowHtml.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)]
    .map(match => htmlToText(match[1] ?? ""))
    .filter(text => text.length > 24);
  const last = paragraphs.at(-1) ?? "";
  const question = bareTitle(headingTitle);
  if (last && !question.startsWith(last.slice(0, 40))) return clip(`${question}. ${last}`, 480);
  return clip(question || last, 480);
}

function takeId(used: Set<string>, base: string): string {
  let id = base;
  let n = 2;
  while (used.has(id)) id = `${base}-${n++}`;
  used.add(id);
  return id;
}

export function parseFfmpegPage(input: { html: string; page: DocPageId | string; url: string }): Chunk[] {
  const headings = findHeadings(input.html);
  const used = new Set<string>();
  const chunks: Chunk[] = [];
  const examples: Chunk[] = [];

  for (let index = 0; index < headings.length; index++) {
    const heading = headings[index];
    if (!heading || heading.level !== 3) continue;
    const body = input.html.slice(heading.end, sectionEnd(headings, index, input.html.length));
    const chapter = chapterAt(headings, heading.index);
    const family = familyOf(chapter?.title ?? "", input.page);
    const bare = bareTitle(heading.title);
    const url = `${input.url}#${heading.anchor}`;
    const wantsOptions = /option/i.test(heading.title) || /option/i.test(chapter?.title ?? "");

    if (wantsOptions) {
      for (const match of body.matchAll(/<dt\b[^>]*>([\s\S]*?)<\/dt>\s*<dd\b[^>]*>([\s\S]*?)<\/dd>/gi)) {
        const signature = optionSignature(match[1] ?? "");
        if (!signature) continue;
        const dd = match[2] ?? "";
        const commands = commandsIn(dd);
        const summary = clip(htmlToText(dd.split(/<pre\b|<dl\b/i)[0] ?? dd), 420);
        const flag = flagOf(signature);
        const documented = commands[0] ?? null;
        const synthesized = documented ? null : optionExample(input.page, flag);
        const command = documented ?? synthesized;
        const id = takeId(used, `${input.page}:opt:${heading.anchor}:${slug(flag)}`);
        chunks.push({
          id,
          kind: "option",
          priority: 0,
          title: flag,
          summary,
          command,
          signature,
          synthesized: !documented && Boolean(synthesized),
          url,
          page: input.page,
          family: "option",
          embedText: buildEmbedText({
            family: "option",
            title: `${flag} (${bare})`,
            summary,
            command,
            extraCommands: commands.slice(1, 3),
            signature,
          }),
        });
      }
    }

    if (!family || SKIP_TITLE.test(bare)) continue;
    const name = componentName(heading.title);
    const commands = commandsIn(body);
    const summary = leadText(body) || `${familyLabel(family)} ${name ?? bare}`;
    const documented = commands.find(item => item.length <= 180) ?? null;
    const canonical = canonicalCommand(name);
    const command = canonical ?? documented ?? templateCommand(family, name);
    const synthesized = Boolean(command) && command !== documented;
    chunks.push({
      id: takeId(used, `${input.page}:${heading.anchor}`),
      kind: "component",
      priority: 1,
      title: name ?? bare,
      summary,
      command,
      signature: null,
      synthesized,
      url,
      page: input.page,
      family,
      embedText: buildEmbedText({
        family,
        title: bare,
        summary,
        command,
        extraCommands: commands.filter(item => item !== command).slice(0, 2),
      }),
    });
  }

  for (const match of input.html.matchAll(/<pre\b[^>]*>([\s\S]*?)<\/pre>/gi)) {
    const start = match.index ?? 0;
    const heading = headingAt(headings, start);
    const commands = commandsFromPre(match[1] ?? "");
    for (const command of commands) {
      const summary = precedingSummary(input.html, start, heading?.title ?? "Example");
      const anchor = heading?.anchor ?? "example";
      examples.push({
        id: takeId(used, `${input.page}:ex:${anchor}:${shortHash(command)}`),
        kind: "example",
        priority: 0,
        title: bareTitle(heading?.title ?? "Example") || "Example",
        summary,
        command,
        signature: null,
        synthesized: false,
        url: `${input.url}#${anchor}`,
        page: input.page,
        family: "example",
        embedText: buildEmbedText({
          family: "example",
          title: bareTitle(heading?.title ?? "Example") || "Example",
          summary,
          command,
        }),
      });
    }
  }

  const bestExample = new Map<string, Chunk>();
  for (const example of examples) {
    const key = example.command ?? example.id;
    const previous = bestExample.get(key);
    if (!previous || example.summary.length > previous.summary.length) bestExample.set(key, example);
  }

  return [...chunks, ...bestExample.values()];
}
