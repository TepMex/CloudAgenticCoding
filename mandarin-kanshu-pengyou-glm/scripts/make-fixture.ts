// Minimal DRM-free EPUB fixture generator for tests.
// Produces a valid EPUB (ZIP) with 3 spine chapters of simple Chinese text.
import { writeFileSync } from "node:fs";
import path from "node:path";

// Tiny zip writer (stored, no compression) — sufficient for fixtures.
function crc32(buf: Uint8Array): number {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let j = 0; j < 8; j++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

type Entry = { name: string; data: Uint8Array };
function zip(entries: Entry[]): Uint8Array {
  const parts: Uint8Array[] = [];
  const central: Uint8Array[] = [];
  let offset = 0;
  for (const e of entries) {
    const name = new TextEncoder().encode(e.name);
    const local = new Uint8Array(30 + name.length + e.data.length);
    const dv = new DataView(local.buffer);
    dv.setUint32(0, 0x04034b50, true);
    dv.setUint16(4, 20, true); // version
    dv.setUint16(6, 0, true); // flags
    dv.setUint16(8, 0, true); // method: stored
    dv.setUint16(10, 0, true); // time
    dv.setUint16(12, 0, true); // date
    dv.setUint32(14, crc32(e.data), true);
    dv.setUint32(18, e.data.length, true);
    dv.setUint32(22, e.data.length, true);
    dv.setUint16(26, name.length, true);
    dv.setUint16(28, 0, true);
    local.set(name, 30);
    local.set(e.data, 30 + name.length);
    parts.push(local);

    const cd = new Uint8Array(46 + name.length);
    const cv = new DataView(cd.buffer);
    cv.setUint32(0, 0x02014b50, true);
    cv.setUint16(4, 20, true);
    cv.setUint16(6, 20, true);
    cv.setUint16(8, 0, true);
    cv.setUint16(10, 0, true);
    cv.setUint16(12, 0, true);
    cv.setUint16(14, 0, true);
    cv.setUint32(16, crc32(e.data), true);
    cv.setUint32(20, e.data.length, true);
    cv.setUint32(24, e.data.length, true);
    cv.setUint16(28, name.length, true);
    cv.setUint16(30, 0, true);
    cv.setUint16(32, 0, true);
    cv.setUint16(34, 0, true);
    cv.setUint16(36, 0, true);
    cv.setUint32(38, 0, true);
    cv.setUint32(42, offset, true);
    cd.set(name, 46);
    central.push(cd);
    offset += local.length;
  }
  const cdBytes = concat(central);
  const end = new Uint8Array(22);
  const ev = new DataView(end.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, entries.length, true);
  ev.setUint16(10, entries.length, true);
  ev.setUint32(12, cdBytes.length, true);
  ev.setUint32(16, offset, true);
  return concat([...parts, cdBytes, end]);
}
function concat(arrs: Uint8Array[]): Uint8Array {
  const len = arrs.reduce((a, b) => a + b.length, 0);
  const out = new Uint8Array(len);
  let o = 0;
  for (const a of arrs) { out.set(a, o); o += a.length; }
  return out;
}

const enc = new TextEncoder();

const chapter = (id: string, title: string, body: string) => `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${title}</title></head>
<body><h1>${title}</h1>${body}</body></html>`;

const ch1 = chapter("ch1", "第一章", "<p>方源看着眼前的春秋蝉。这是故事中的一种特殊蛊虫。他微微一笑。</p><p>他想起了过去的日子。那时候他还很年轻。</p>");
const ch2 = chapter("ch2", "第二章", "<p>方源走进了山洞。洞里很黑。他点燃了一根火把。</p><p>火光映照在墙壁上，显出古老的文字。</p>");
const ch3 = chapter("ch3", "第三章", "<p>第二天清晨，方源离开了山洞。外面下着小雨。</p><p>他撑起一把油纸伞，沿着山路继续前行。</p>");

const containerXml = `<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps+xml"/></rootfiles>
</container>`;

const contentOpf = `<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="bookid">urn:uuid:test-book-1</dc:identifier>
<dc:title>春秋蝉</dc:title>
<dc:creator>测试作者</dc:creator>
<dc:language>zh</dc:language>
</metadata>
<manifest>
<item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
<item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
<item id="ch3" href="ch3.xhtml" media-type="application/xhtml+xml"/>
<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
</manifest>
<spine>
<itemref idref="ch1"/><itemref idref="ch2"/><itemref idref="ch3"/>
</spine>
</package>`;

const navXhtml = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>目</title></head><body>
<nav epub:type="toc">
<ol>
<li><a href="ch1.xhtml">第一章</a></li>
<li><a href="ch2.xhtml">第二章</a></li>
<li><a href="ch3.xhtml">第三章</a></li>
</ol></nav></body></html>`;

const entries: Entry[] = [
  { name: "mimetype", data: enc.encode("application/epub+zip") },
  { name: "META-INF/container.xml", data: enc.encode(containerXml) },
  { name: "OEBPS/content.opf", data: enc.encode(contentOpf) },
  { name: "OEBPS/nav.xhtml", data: enc.encode(navXhtml) },
  { name: "OEBPS/ch1.xhtml", data: enc.encode(ch1) },
  { name: "OEBPS/ch2.xhtml", data: enc.encode(ch2) },
  { name: "OEBPS/ch3.xhtml", data: enc.encode(ch3) },
];

const bytes = zip(entries);
const outDir = path.resolve(process.argv[2] ?? "tests/fixtures");
writeFileSync(path.join(outDir, "sample.epub"), bytes);
console.log("wrote", path.join(outDir, "sample.epub"), bytes.length, "bytes");