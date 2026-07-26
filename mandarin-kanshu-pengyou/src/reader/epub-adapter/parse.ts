import JSZip from "jszip";
import DOMPurify from "dompurify";
import { createId } from "../../shared/id";

export type ParsedSpineItem = {
  id: string;
  href: string;
  title: string;
  order: number;
  html: string;
  plainText: string;
};

export type ParsedEpub = {
  title: string;
  author: string;
  language: string;
  spine: ParsedSpineItem[];
};

function getDir(path: string): string {
  const i = path.lastIndexOf("/");
  return i >= 0 ? path.slice(0, i + 1) : "";
}

function resolveHref(base: string, href: string): string {
  if (!href) return base;
  const clean = href.split("#")[0] ?? href;
  if (clean.startsWith("/")) return clean.slice(1);
  const baseDir = getDir(base);
  const parts = (baseDir + clean).split("/");
  const out: string[] = [];
  for (const p of parts) {
    if (p === "" || p === ".") continue;
    if (p === "..") out.pop();
    else out.push(p);
  }
  return out.join("/");
}

function textContent(html: string): string {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const body = doc.body;
  // Prefer paragraphs
  const blocks: string[] = [];
  body.querySelectorAll("p, h1, h2, h3, h4, li, div").forEach((el) => {
    const t = (el.textContent ?? "").replace(/\s+/g, " ").trim();
    if (t) blocks.push(t);
  });
  if (blocks.length) return blocks.join("\n\n");
  return (body.textContent ?? "").replace(/\s+/g, " ").trim();
}

function sanitizeChapterHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: ["script", "iframe", "object", "embed", "link", "meta"],
    FORBID_ATTR: ["onerror", "onclick", "onload", "style"],
    ALLOW_UNKNOWN_PROTOCOLS: false,
  });
}

function parseXml(xml: string): Document {
  return new DOMParser().parseFromString(xml, "application/xml");
}

function attr(el: Element | null, name: string): string {
  return el?.getAttribute(name) ?? "";
}

/** Read DC metadata without CSS namespace selectors (happy-dom / XML-safe). */
function firstMetaText(doc: Document, localNames: string[]): string {
  const all = [...doc.getElementsByTagName("*")];
  for (const local of localNames) {
    const short = local.includes(":") ? local.split(":")[1]! : local;
    const hit = all.find((el) => {
      const name = el.localName || el.tagName;
      return name === local || name === short || name.toLowerCase() === short.toLowerCase();
    });
    const text = hit?.textContent?.trim();
    if (text) return text;
  }
  return "";
}

async function readZipText(zip: JSZip, path: string): Promise<string | null> {
  const normalized = path.replace(/^\//, "");
  const file =
    zip.file(normalized) ??
    zip.file(decodeURIComponent(normalized)) ??
    Object.values(zip.files).find((f) => f.name.toLowerCase() === normalized.toLowerCase());
  if (!file || file.dir) return null;
  return file.async("text");
}

function extractNavTitles(
  zip: JSZip,
  opfPath: string,
  opfDoc: Document,
): Promise<Map<string, string>> {
  return (async () => {
    const map = new Map<string, string>();
    const items = [...opfDoc.querySelectorAll("manifest > item, item")];
    const navItem =
      items.find((i) => (i.getAttribute("properties") ?? "").includes("nav")) ??
      items.find((i) => (i.getAttribute("media-type") ?? "").includes("ncx"));

    if (!navItem) return map;
    const href = resolveHref(opfPath, attr(navItem, "href"));
    const navXml = await readZipText(zip, href);
    if (!navXml) return map;

    const navDoc = parseXml(navXml);
    // EPUB3 nav + any links; avoid namespaced attribute selectors for parser portability
    navDoc.querySelectorAll("nav a, a").forEach((a) => {
      const h = a.getAttribute("href");
      const title = (a.textContent ?? "").trim();
      if (h && title) {
        const resolved = resolveHref(href, h.split("#")[0] ?? h);
        map.set(resolved, title);
      }
    });
    // NCX
    navDoc.querySelectorAll("navPoint").forEach((np) => {
      const label = np.querySelector("navLabel > text")?.textContent?.trim() ?? "";
      const src = np.querySelector("content")?.getAttribute("src") ?? "";
      if (label && src) {
        map.set(resolveHref(href, src.split("#")[0] ?? src), label);
      }
    });
    return map;
  })();
}

export async function parseEpub(data: ArrayBuffer | Blob): Promise<ParsedEpub> {
  const zip = await JSZip.loadAsync(data);
  const containerXml = await readZipText(zip, "META-INF/container.xml");
  if (!containerXml) throw new Error("Invalid EPUB: missing META-INF/container.xml");

  const container = parseXml(containerXml);
  const rootfile = container.querySelector("rootfile")?.getAttribute("full-path");
  if (!rootfile) throw new Error("Invalid EPUB: missing rootfile");

  const opfText = await readZipText(zip, rootfile);
  if (!opfText) throw new Error("Invalid EPUB: missing OPF");
  const opf = parseXml(opfText);

  const title = firstMetaText(opf, ["title", "dc:title"]) || "Untitled";
  const author = firstMetaText(opf, ["creator", "dc:creator"]) || "Unknown";
  const language = firstMetaText(opf, ["language", "dc:language"]) || "zh";

  const manifest = new Map<string, { href: string; mediaType: string }>();
  opf.querySelectorAll("manifest > item, item[id]").forEach((item) => {
    const id = attr(item, "id");
    if (!id) return;
    manifest.set(id, {
      href: resolveHref(rootfile, attr(item, "href")),
      mediaType: attr(item, "media-type"),
    });
  });

  const titleMap = await extractNavTitles(zip, rootfile, opf);

  const spineEls = [...opf.querySelectorAll("spine > itemref, itemref")];
  const spine: ParsedSpineItem[] = [];
  let order = 0;

  for (const ref of spineEls) {
    const idref = attr(ref, "idref");
    const meta = manifest.get(idref);
    if (!meta) continue;
    if (meta.mediaType && !meta.mediaType.includes("html") && !meta.mediaType.includes("xml")) {
      continue;
    }
    const raw = await readZipText(zip, meta.href);
    if (!raw) continue;
    const sanitized = sanitizeChapterHtml(raw);
    const plainText = textContent(sanitized);
    const navTitle = titleMap.get(meta.href);
    const heading =
      new DOMParser()
        .parseFromString(sanitized, "text/html")
        .querySelector("h1, h2, h3, title")
        ?.textContent?.trim() ?? "";
    spine.push({
      id: idref || createId("spine"),
      href: meta.href,
      title: navTitle || heading || `Section ${order + 1}`,
      order,
      html: sanitized,
      plainText,
    });
    order++;
  }

  if (!spine.length) throw new Error("Invalid EPUB: empty spine");

  return { title, author, language, spine };
}

export const READER_IFRAME_CSS = `
  :root { color-scheme: light dark; }
  html, body {
    margin: 0;
    padding: 0;
    background: transparent;
    color: inherit;
    font-family: "Source Han Serif SC", "Noto Serif CJK SC", "Songti SC", "STSong",
      "PingFang SC", "Hiragino Sans GB", Georgia, serif;
    overflow-x: hidden;
    -webkit-text-size-adjust: 100%;
  }
  body {
    padding: 1.25rem 1.5rem 4rem;
    line-height: var(--reader-line-height, 1.75);
    font-size: var(--reader-font-size, 20px);
    max-width: var(--reader-max-width, 42ch);
    margin: 0 auto;
  }
  p, div, li, h1, h2, h3, h4 {
    max-width: 100%;
    margin: 0 0 0.9em;
  }
  img, svg, table { max-width: 100%; height: auto; }
  a { color: inherit; text-decoration: underline; text-underline-offset: 2px; }
  .mkp-hl-manual {
    background: color-mix(in srgb, #c4a35a 55%, transparent);
    border-radius: 2px;
  }
  .mkp-hl-expanded {
    background: color-mix(in srgb, #c4a35a 22%, transparent);
    border-radius: 2px;
  }
  .mkp-ann-mark {
    background: color-mix(in srgb, #6b8f71 28%, transparent);
    border-bottom: 2px solid color-mix(in srgb, #6b8f71 55%, transparent);
  }
  ::selection {
    background: color-mix(in srgb, #c4a35a 45%, transparent);
  }
`;

export type RendererHandle = {
  iframe: HTMLIFrameElement;
  setChapterHtml: (html: string, opts: {
    fontSizePx: number;
    lineHeight: number;
    contentWidthCh: number;
    appearance: "light" | "dark";
  }) => void;
  getDocument: () => Document | null;
  destroy: () => void;
};

/** Hide iframe vs DOM choice behind this adapter. */
export function createIframeRenderer(container: HTMLElement): RendererHandle {
  const iframe = document.createElement("iframe");
  iframe.title = "Book chapter";
  iframe.setAttribute("sandbox", "allow-same-origin");
  iframe.style.width = "100%";
  iframe.style.border = "0";
  iframe.style.minHeight = "60vh";
  iframe.style.background = "transparent";
  container.appendChild(iframe);

  const write = (html: string, opts: {
    fontSizePx: number;
    lineHeight: number;
    contentWidthCh: number;
    appearance: "light" | "dark";
  }) => {
    const doc = iframe.contentDocument;
    if (!doc) return;
    const fg = opts.appearance === "dark" ? "#e8e2d6" : "#3a2f24";
    const bg = "transparent";
    doc.open();
    doc.write(`<!DOCTYPE html><html><head><meta charset="utf-8"/>
      <style>${READER_IFRAME_CSS}
        body { color: ${fg}; background: ${bg};
          --reader-font-size: ${opts.fontSizePx}px;
          --reader-line-height: ${opts.lineHeight};
          --reader-max-width: ${opts.contentWidthCh}ch;
        }
      </style></head><body>${html}</body></html>`);
    doc.close();
    const resize = () => {
      const h = doc.body?.scrollHeight ?? 600;
      iframe.style.height = `${Math.max(h + 24, 400)}px`;
    };
    requestAnimationFrame(resize);
    setTimeout(resize, 50);
  };

  return {
    iframe,
    setChapterHtml: write,
    getDocument: () => iframe.contentDocument,
    destroy: () => {
      iframe.remove();
    },
  };
}
