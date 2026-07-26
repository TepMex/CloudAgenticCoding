#!/usr/bin/env bun
import JSZip from "jszip";
import { mkdir } from "node:fs/promises";
import { join } from "node:path";

const root = join(import.meta.dir, "..");
const outDir = join(root, "fixtures");
const outFile = join(outDir, "sample.epub");

const chapter1 = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh">
<head><title>第一章</title></meta></head>
<body>
  <h1>第一章 初遇</h1>
  <p>清晨的江边，雾气还没有散尽。李明站在石阶上，看着远处慢慢靠近的小船。</p>
  <p>船上传来一个温和的声音：“你就是传说中的春秋蝉传人？”</p>
  <p>李明摇了摇头，笑道：“我只是一个普通的读书人。春秋蝉？那不过是故事里的名字罢了。”</p>
  <p>对方跳上岸，衣裳被水汽打湿。他自称王衡，来自青云谷。</p>
  <p>两个人并肩走了一段路。王衡忽然停住，压低声音说：“城里最近不太平。你最好别在夜里出门。”</p>
</body>
</html>`;

const chapter2 = `<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>第二章</title></head>
<body>
  <h1>第二章 青云谷</h1>
  <p>第二天，李明跟着王衡离开江边，向山里走去。</p>
  <p>青云谷并不大，却藏着一座安静的书院。院子里种着几棵老槐树。</p>
  <p>一位女先生出来迎接他们。她叫苏晚，说话不紧不慢。</p>
  <p>“你们来得正好，”苏晚说，“书院缺人手抄书。李明，你愿意帮忙吗？”</p>
</body>
</html>`;

const contentOpf = `<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookid" version="2.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>江边小记</dc:title>
    <dc:creator>测试作者</dc:creator>
    <dc:language>zh</dc:language>
    <dc:identifier id="bookid">urn:uuid:mkp-sample-001</dc:identifier>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="c1"/>
    <itemref idref="c2"/>
  </spine>
</package>`;

const tocNcx = `<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <docTitle><text>江边小记</text></docTitle>
  <navMap>
    <navPoint id="nav1" playOrder="1">
      <navLabel><text>第一章 初遇</text></navLabel>
      <content src="chapter1.xhtml"/>
    </navPoint>
    <navPoint id="nav2" playOrder="2">
      <navLabel><text>第二章 青云谷</text></navLabel>
      <content src="chapter2.xhtml"/>
    </navPoint>
  </navMap>
</ncx>`;

const container = `<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>`;

const zip = new JSZip();
zip.file("mimetype", "application/epub+zip", { compression: "STORE" });
zip.folder("META-INF")!.file("container.xml", container);
const oebps = zip.folder("OEBPS")!;
oebps.file("content.opf", contentOpf);
oebps.file("toc.ncx", tocNcx);
oebps.file("chapter1.xhtml", chapter1.replace("</meta>", ""));
oebps.file("chapter2.xhtml", chapter2);

await mkdir(outDir, { recursive: true });
const buf = await zip.generateAsync({ type: "uint8array", compression: "DEFLATE" });
await Bun.write(outFile, buf);
console.log(`Wrote ${outFile}`);
