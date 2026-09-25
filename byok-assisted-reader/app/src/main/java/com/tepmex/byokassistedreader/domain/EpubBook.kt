package com.tepmex.byokassistedreader.domain

import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class EpubBook(val title: String, val paragraphs: List<String>) {
    val plainText: String get() = paragraphs.joinToString("\n")
}

fun parseEpub(bytes: ByteArray): EpubBook {
    val entries = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) {
                val name = entry.name.replace('\\', '/').trimStart('/')
                entries[name] = zip.readBytes()
            }
            zip.closeEntry()
        }
    }
    val container = entries["META-INF/container.xml"]?.toString(StandardCharsets.UTF_8)
        ?: throw IllegalArgumentException("В EPUB нет container.xml")
    val opfPath = Regex("""full-path\s*=\s*["']([^"']+)["']""")
        .find(container)?.groupValues?.get(1)
        ?.let { urlDecode(it).replace('\\', '/').trimStart('/') }
        ?: throw IllegalArgumentException("В EPUB нет rootfile")
    val opf = entries[opfPath]?.toString(StandardCharsets.UTF_8)
        ?: throw IllegalArgumentException("Не найден $opfPath")
    val title = Regex("""<(?:[\w.-]+:)?title[^>]*>([^<]*)</""")
        .find(opf)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
        ?.takeIf { it.isNotEmpty() }
        ?: "Книга"
    val hrefById = HashMap<String, String>()
    val item = Regex("""<item\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    for (match in item.findAll(opf)) {
        val attrs = match.groupValues[1]
        val id = attr(attrs, "id") ?: continue
        val href = attr(attrs, "href") ?: continue
        val media = attr(attrs, "media-type").orEmpty()
        val path = href.substringBefore('#').substringBefore('?')
        val isHtml = media.contains("html", ignoreCase = true) ||
            path.endsWith(".xhtml", true) ||
            path.endsWith(".html", true) ||
            path.endsWith(".htm", true)
        if (isHtml) hrefById[id] = urlDecode(path)
    }
    val paragraphs = ArrayList<String>()
    val idref = Regex("""<itemref\b[^>]*\bidref\s*=\s*["']([^"']+)["']""")
    for (match in idref.findAll(opf)) {
        val href = hrefById[match.groupValues[1]] ?: continue
        val path = resolvePath(opfPath, href)
        val html = entries[path]?.toString(StandardCharsets.UTF_8) ?: continue
        paragraphs.addAll(htmlToParagraphs(html))
    }
    if (paragraphs.isEmpty()) throw IllegalArgumentException("В EPUB нет текста")
    return EpubBook(title, paragraphs)
}

private fun attr(attrs: String, name: String): String? =
    Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(attrs)?.groupValues?.get(1)

private fun urlDecode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

fun resolvePath(opfPath: String, href: String): String {
    val clean = href.trimStart('/')
    if (href.startsWith("/")) return clean
    val dir = opfPath.substringBeforeLast('/', "")
    val parts = ArrayList<String>()
    if (dir.isNotEmpty()) parts.addAll(dir.split('/'))
    for (piece in clean.split('/')) {
        when (piece) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts.add(piece)
        }
    }
    return parts.joinToString("/")
}
