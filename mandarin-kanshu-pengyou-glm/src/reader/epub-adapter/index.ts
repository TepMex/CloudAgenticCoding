import JSZip from "jszip";
import DOMPurify from "dompurify";
import type { ReaderLocation } from "../../shared/domain";

export type ChapterRef = { id: string; href: string; label: string; index: number };
export type RenderedChapter = { id: string; iframe: HTMLIFrameElement; text: string; paragraphs: string[] };

export type EpubRendererAdapter = {
  open(file: Blob): Promise<void>;
  metadata(): { title: string; author: string; coverDataUrl?: string };
  chapters(): ChapterRef[];
  renderChapter(id: string, target: HTMLElement): Promise<RenderedChapter>;
  chapterByIndex(index: number): ChapterRef | undefined;
  adjacentChapters(id: string): { prev?: ChapterRef; next?: ChapterRef };
  chapterText(id: string): Promise<{ paragraphs: string[]; text: string }>;
  locationFromSelection(bookId: string, chapter: ChapterRef, selection: Selection): ReaderLocation | null;
  destroy(): void;
};

const READER_CSS = `
html, body { margin: 0; padding: 0; background: transparent; }
body {
  font-family: "Songti SC", "Noto Serif CJK SC", "Source Han Serif SC", serif;
  line-height: var(--reader-line-height, 1.9);
  font-size: var(--reader-font-size, 19px);
  max-width: var(--reader-content-width, 720px);
  margin: 0 auto; padding: 2rem 1.25rem 6rem;
  color: var(--reader-fg, #2c2418);
  word-break: break-word; text-align: justify;
}
p { margin: 0 0 1em; text-indent: 2em; }
h1, h2, h3 { line-height: 1.3; font-weight: 600; text-indent: 0; }
h1 { font-size: 1.4em; margin: 1em 0 0.6em; }
img { max-width: 100%; height: auto; }
a { color: inherit; text-decoration: none; }
.mkp-highlight-strong { background: rgba(180,140,60,0.42); border-radius: 2px; }
.mkp-highlight-passage { background: rgba(180,140,60,0.18); border-radius: 2px; }
.mkp-marker { border-left: 3px solid rgba(180,140,60,0.5); padding-left: 2px; }
`;

function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    FORBID_TAGS: ["script", "style", "link", "iframe", "object", "embed"],
    FORBID_ATTR: ["onload", "onerror", "onclick", "style"],
    ALLOW_DATA_ATTR: false,
  });
}

function resolveHref(base: string, href: string): string {
  if (!href) return base;
  const cleanHref = href.split("#")[0];
  const parts = base.split("/");
  parts.pop();
  for (const seg of cleanHref.split("/")) {
    if (seg === "..") parts.pop();
    else if (seg === "." || seg === "") continue;
    else parts.push(seg);
  }
  return parts.join("/");
}

async function blobToDataUrl(blob: Blob): Promise<string | undefined> {
  if (!blob) return undefined;
  return new Promise<string | undefined>((resolve) => {
    const r = new FileReader();
    r.onload = () => resolve(r.result as string);
    r.onerror = () => resolve(undefined);
    r.readAsDataURL(blob);
  });
}

type OpenedBook = {
  zip: JSZip; opfPath: string; title: string; author: string; coverDataUrl?: string;
  spine: { idref: string; href: string }[];
  manifest: Map<string, { href: string; mediaType: string }>;
  toc: { label: string; href: string }[];
};

async function parseOpf(zip: JSZip, opfPath: string): Promise<Omit<OpenedBook, "zip" | "opfPath" | "coverDataUrl">> {
  const opfFile = zip.file(opfPath);
  if (!opfFile) throw new Error("OPF not found in EPUB");
  const opfText = await opfFile.async("string");
  const doc = new DOMParser().parseFromString(opfText, "application/xhtml+xml");
  const metaEl = doc.querySelector("metadata");
  const title = metaEl?.getElementsByTagName("dc:title")[0]?.textContent?.trim() || metaEl?.querySelector("title")?.textContent?.trim() || "Untitled";
  const author = metaEl?.getElementsByTagName("dc:creator")[0]?.textContent?.trim() || "";
  const manifest = new Map<string, { href: string; mediaType: string }>();
  doc.querySelectorAll("manifest > item").forEach((item) => {
    manifest.set(item.getAttribute("id") || "", {
      href: resolveHref(opfPath, item.getAttribute("href") || ""),
      mediaType: item.getAttribute("media-type") || "",
    });
  });
  const spine: { idref: string; href: string }[] = [];
  doc.querySelectorAll("spine > itemref").forEach((item) => {
    const idref = item.getAttribute("idref") || "";
    const m = manifest.get(idref);
    if (m) spine.push({ idref, href: m.href });
  });
  const toc: { label: string; href: string }[] = [];
  const navItem = Array.from(doc.querySelectorAll("manifest > item")).find((it) => (it.getAttribute("properties") || "").includes("nav"));
  if (navItem) {
    const navHref = resolveHref(opfPath, navItem.getAttribute("href") || "");
    const navFile = zip.file(navHref);
    if (navFile) {
      const navDoc = new DOMParser().parseFromString(await navFile.async("string"), "application/xhtml+xml");
      navDoc.querySelectorAll("nav ol > li > a, nav li > a").forEach((a) => {
        const label = (a.textContent || "").trim();
        const href = a.getAttribute("href") || "";
        if (label && href) toc.push({ label, href: resolveHref(navHref, href.split("#")[0]) });
      });
    }
  }
  if (toc.length === 0) {
    const ncxId = doc.querySelector("spine")?.getAttribute("toc") || "";
    const ncxItem = manifest.get(ncxId);
    if (ncxItem) {
      const ncxFile = zip.file(ncxItem.href);
      if (ncxFile) {
        const ncxDoc = new DOMParser().parseFromString(await ncxFile.async("string"), "application/xml");
        ncxDoc.querySelectorAll("navPoint").forEach((np) => {
          const label = np.querySelector("navLabel > text")?.textContent?.trim() || "";
          const src = np.querySelector("content")?.getAttribute("src") || "";
          if (label && src) toc.push({ label, href: resolveHref(ncxItem.href, src.split("#")[0]) });
        });
      }
    }
  }
  return { title, author, spine, manifest, toc };
}

async function findCover(zip: JSZip, book: Omit<OpenedBook, "zip" | "opfPath" | "coverDataUrl">): Promise<string | undefined> {
  for (const [, item] of book.manifest) {
    if ((item.mediaType || "").startsWith("image/")) {
      const f = zip.file(item.href);
      if (f) {
        const blob = await f.async("blob");
        return blobToDataUrl(new Blob([blob], { type: item.mediaType }));
      }
    }
  }
  return undefined;
}

async function extractChapterText(html: string): Promise<{ paragraphs: string[]; text: string }> {
  const doc = new DOMParser().parseFromString(html, "application/xhtml+xml");
  const body = doc.body || doc.documentElement;
  const paras: string[] = [];
  body.querySelectorAll("p, div, h1, h2, h3, li, blockquote").forEach((el) => {
    const t = (el.textContent || "").replace(/\s+/g, " ").trim();
    if (t) paras.push(t);
  });
  if (paras.length === 0) {
    const t = (body.textContent || "").replace(/\s+/g, " ").trim();
    if (t) paras.push(t);
  }
  return { paragraphs: paras, text: paras.join("\n\n") };
}

export async function createEpubAdapter(): Promise<EpubRendererAdapter> {
  let opened: OpenedBook | undefined;
  const chapterCache = new Map<string, string>();

  function ensure(): OpenedBook {
    if (!opened) throw new Error("No EPUB is open");
    return opened;
  }

  return {
    async open(file) {
      const buf = await file.arrayBuffer();
      const zip = await JSZip.loadAsync(buf);
      const containerFile = zip.file("META-INF/container.xml");
      if (!containerFile) throw new Error("Invalid EPUB: missing container.xml");
      const containerDoc = new DOMParser().parseFromString(await containerFile.async("string"), "application/xml");
      const opfPath = containerDoc.querySelector("rootfile")?.getAttribute("full-path") || "";
      if (!opfPath) throw new Error("Invalid EPUB: no rootfile");
      const parsed = await parseOpf(zip, opfPath);
      const coverDataUrl = await findCover(zip, parsed);
      opened = { zip, opfPath, ...parsed, coverDataUrl };
      chapterCache.clear();
    },
    metadata() {
      const b = ensure();
      return { title: b.title, author: b.author, coverDataUrl: b.coverDataUrl };
    },
    chapters() {
      const b = ensure();
      const tocByHref = new Map(b.toc.map((t) => [t.href, t.label]));
      return b.spine.map((s, i) => ({ id: s.idref, href: s.href, index: i, label: tocByHref.get(s.href) || `第 ${i + 1} 章` }));
    },
    chapterByIndex(index) {
      const b = ensure();
      return b.spine[index] ? { id: b.spine[index].idref, href: b.spine[index].href, index, label: "" } : undefined;
    },
    adjacentChapters(id) {
      const b = ensure();
      const idx = b.spine.findIndex((s) => s.idref === id);
      if (idx < 0) return {};
      const toRef = (i: number): ChapterRef | undefined =>
        b.spine[i] ? { id: b.spine[i].idref, href: b.spine[i].href, index: i, label: "" } : undefined;
      return { prev: toRef(idx - 1), next: toRef(idx + 1) };
    },
    async chapterText(id) {
      const b = ensure();
      const item = b.spine.find((s) => s.idref === id);
      if (!item) throw new Error("Unknown chapter");
      let html = chapterCache.get(item.href);
      if (!html) {
        const f = b.zip.file(item.href);
        if (!f) throw new Error("Chapter file missing: " + item.href);
        html = await f.async("string");
        chapterCache.set(item.href, html);
      }
      return extractChapterText(html);
    },
    async renderChapter(id, target) {
      const b = ensure();
      const item = b.spine.find((s) => s.idref === id);
      if (!item) throw new Error("Unknown chapter id: " + id);
      let html = chapterCache.get(item.href);
      if (!html) {
        const f = b.zip.file(item.href);
        if (!f) throw new Error("Chapter file missing: " + item.href);
        html = await f.async("string");
        chapterCache.set(item.href, html);
      }
      const { paragraphs, text } = await extractChapterText(html);
      const bodyMatch = html.match(/<body[^>]*>([\s\S]*?)<\/body>/i);
      const bodyHtml = bodyMatch ? bodyMatch[1] : html;
      const clean = sanitizeHtml(bodyHtml);
      target.replaceChildren();
      const iframe = document.createElement("iframe");
      iframe.setAttribute("sandbox", "allow-same-origin");
      iframe.setAttribute("aria-label", "EPUB chapter content");
      iframe.style.width = "100%";
      iframe.style.height = "100%";
      iframe.style.border = "0";
      iframe.style.background = "transparent";
      iframe.style.colorScheme = "light dark";
      target.appendChild(iframe);
      const doc = iframe.contentDocument!;
      doc.open();
      doc.write(`<!doctype html><html><head><meta charset="utf-8"><style>${READER_CSS}</style></head><body>${clean}</body></html>`);
      doc.close();
      return { id, iframe, text, paragraphs };
    },
    locationFromSelection(bookId, chapter, selection) {
      if (!selection || selection.isCollapsed || selection.rangeCount === 0) return null;
      const range = selection.getRangeAt(0);
      const text = range.toString();
      if (!text.trim()) return null;
      const doc = range.startContainer.ownerDocument!;
      const fullText = (doc.body?.textContent || "").replace(/\s+/g, " ");
      const start = fullText.indexOf(text);
      const prefix = start > 0 ? fullText.slice(Math.max(0, start - 60), start) : "";
      const suffix = start >= 0 ? fullText.slice(start + text.length, start + text.length + 60) : "";
      return {
        bookId, spineItemId: chapter.id,
        epubCfi: `epubcfi(/${chapter.index}/x(text)${start >= 0 ? start : 0})`,
        textQuote: text.trim(),
        prefix: prefix.trim().slice(-60),
        suffix: suffix.trim().slice(0, 60),
        approximateProgress: chapter.index / Math.max(1, ensure().spine.length),
      };
    },
    destroy() { opened = undefined; chapterCache.clear(); },
  };
}
