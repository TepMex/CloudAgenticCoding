// Builds a minimal valid DRM-free EPUB as a Blob for tests.
import JSZip from "jszip";

export async function makeMockEpub(): Promise<Blob> {
  const zip = new JSZip();
  zip.file("mimetype", "application/epub+zip");
  zip.file("META-INF/container.xml", `<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>`);
  zip.file("OEBPS/content.opf", `<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>测试小说</dc:title>
    <dc:creator>测试作者</dc:creator>
    <dc:identifier id="bookid">test-1</dc:identifier>
    <dc:language>zh</dc:language>
  </metadata>
  <manifest>
    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
  </manifest>
  <spine>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
  </spine>
</package>`);
  zip.file("OEBPS/ch1.xhtml", `<?xml version="1.0"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第一章</title></head>
<body><h1>第一章 开始</h1><p>方源走进了春秋蝉的房间。他看着那只蛊虫，心中暗自盘算。春秋蝉◆（故事中的一种特殊蛊虫）是稀有的。</p><p>他决定继续修炼。这条路很长，但他不会放弃。</p></body></html>`);
  zip.file("OEBPS/ch2.xhtml", `<?xml version="1.0"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第二章</title></head>
<body><h1>第二章 旅程</h1><p>第二天，方源离开了村庄。他带着春秋蝉，踏上了北上的道路。</p><p>路上遇到了一个老人。老人说：“年轻人，你要去哪里？”</p></body></html>`);
  zip.file("OEBPS/nav.xhtml", `<?xml version="1.0"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>目录</title></head>
<body><nav epub:type="toc"><ol><li><a href="ch1.xhtml">第一章 开始</a></li><li><a href="ch2.xhtml">第二章 旅程</a></li></ol></nav></body></html>`);
  const buf = await zip.generateAsync({ type: "blob", mimeType: "application/epub+zip" });
  return buf;
}