import JSZip from "jszip";

export type EpubChapter = {
  id: string;
  title: string;
  /** Plain text extracted from the spine document */
  text: string;
};

function stripTagsToText(html: string): string {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const text = doc.body?.textContent ?? "";
  return text.replace(/\u00a0/g, " ").replace(/\s+\n/g, "\n").replace(/[ \t]{2,}/g, " ").trim();
}

async function readTextFile(zip: JSZip, path: string): Promise<string | null> {
  const normalized = path.replace(/^\//, "");
  const file = zip.file(normalized);
  if (!file) return null;
  return file.async("string");
}

function dirname(p: string): string {
  const idx = p.lastIndexOf("/");
  return idx === -1 ? "" : p.slice(0, idx);
}

function resolveRelative(opfDir: string, href: string): string {
  const base = opfDir.replace(/\/$/, "");
  const clean = href.replace(/^\//, "");
  if (!base) return clean;
  return `${base}/${clean}`.replace(/\/+/g, "/");
}

function parseXmlAttr(tag: string, attr: string): string | null {
  const re = new RegExp(`${attr}\\s*=\\s*["']([^"']*)["']`, "i");
  const m = tag.match(re);
  return m?.[1] ?? null;
}

/** Minimal OPF / container parsing for reflowable EPUBs (EPUB 2/3). */
export async function parseEpub(file: File): Promise<EpubChapter[]> {
  const buf = await file.arrayBuffer();
  const zip = await JSZip.loadAsync(buf);

  const containerXml = await readTextFile(zip, "META-INF/container.xml");
  if (!containerXml) {
    throw new Error("Not a valid EPUB (missing META-INF/container.xml)");
  }

  const rootfile = containerXml.match(/full-path\s*=\s*["']([^"']+)["']/i)?.[1];
  if (!rootfile) {
    throw new Error("Could not find OPF path in container.xml");
  }

  const opfPath = rootfile.replace(/^\//, "");
  const opfDir = dirname(opfPath);
  const opfXml = await readTextFile(zip, opfPath);
  if (!opfXml) {
    throw new Error("OPF package file missing");
  }

  const manifestItems = new Map<string, { href: string; mediaType: string }>();
  const manifestBlock = opfXml.match(/<manifest[^>]*>([\s\S]*?)<\/manifest>/i)?.[1] ?? "";
  const itemRegex = /<item\b[^>]*>/gi;
  let m: RegExpExecArray | null;
  while ((m = itemRegex.exec(manifestBlock))) {
    const tag = m[0];
    const id = parseXmlAttr(tag, "id");
    const href = parseXmlAttr(tag, "href");
    const mediaType =
      parseXmlAttr(tag, "media-type") ?? parseXmlAttr(tag, "mediaType") ?? "application/octet-stream";
    if (id && href) {
      manifestItems.set(id, { href, mediaType });
    }
  }

  const spineIds: string[] = [];
  const spineBlock = opfXml.match(/<spine[^>]*>([\s\S]*?)<\/spine>/i)?.[1] ?? "";
  const itemrefRegex = /<itemref\b[^>]*>/gi;
  while ((m = itemrefRegex.exec(spineBlock))) {
    const idref = parseXmlAttr(m[0], "idref");
    if (idref) spineIds.push(idref);
  }

  const chapters: EpubChapter[] = [];
  let index = 0;

  for (const id of spineIds) {
    const item = manifestItems.get(id);
    if (!item) continue;

    const mt = item.mediaType.toLowerCase();
    if (mt !== "application/xhtml+xml" && mt !== "application/xhtml" && mt !== "text/html") {
      continue;
    }

    const absPath = resolveRelative(opfDir, item.href);
    const xhtml = await readTextFile(zip, absPath);
    if (!xhtml) continue;

    const text = stripTagsToText(xhtml);
    if (!text) continue;

    const titleMatch = xhtml.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
    const h1Match = xhtml.match(/<h1[^>]*>([\s\S]*?)<\/h1>/i);
    const titleRaw = (titleMatch?.[1] ?? h1Match?.[1] ?? item.href).replace(/\s+/g, " ").trim();

    chapters.push({
      id: `${index}-${id}`,
      title: titleRaw || `Section ${index + 1}`,
      text,
    });
    index += 1;
  }

  if (chapters.length === 0) {
    throw new Error("No readable chapters found in this EPUB (unexpected structure?)");
  }

  return chapters;
}
