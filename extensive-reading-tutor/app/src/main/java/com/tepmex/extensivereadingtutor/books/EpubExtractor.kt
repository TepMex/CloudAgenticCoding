package com.tepmex.extensivereadingtutor.books

import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.EpubMetadata
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class ExtractedBlock(
    val spineIndex: Int,
    val href: String,
    val chapterTitle: String?,
    val text: String,
)

data class ExtractedBook(
    val title: String,
    val author: String,
    val language: String?,
    val metadata: EpubMetadata,
    val blocks: List<ExtractedBlock>,
)

interface EpubTextExtractor {
    fun extract(bytes: ByteArray): AppResult<ExtractedBook>
}

object EpubExtractor : EpubTextExtractor {
    override fun extract(bytes: ByteArray): AppResult<ExtractedBook> {
        return try {
            val entries = readZip(bytes)
            val container = entries["META-INF/container.xml"]
                ?: return fail("В EPUB нет container.xml")
            val opfPath = Regex("""full-path\s*=\s*["']([^"']+)["']""")
                .find(container)?.groupValues?.get(1)
                ?.let { urlDecode(it).replace('\\', '/').trimStart('/') }
                ?: return fail("В EPUB нет rootfile")
            val opf = entries[opfPath] ?: return fail("Не найден $opfPath")
            val title = tagText(opf, "title").ifBlank { "Untitled" }
            val author = tagText(opf, "creator")
            val rawLanguage = tagText(opf, "language").ifBlank { null }
            val metadata = EpubMetadata(
                identifier = tagText(opf, "identifier").ifBlank { null },
                publisher = tagText(opf, "publisher").ifBlank { null },
                description = tagText(opf, "description").ifBlank { null },
                rawLanguage = rawLanguage,
            )
            val hrefById = linkedMapOf<String, String>()
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
            val blocks = mutableListOf<ExtractedBlock>()
            val idref = Regex("""<itemref\b[^>]*\bidref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            var spineIndex = 0
            for (match in idref.findAll(opf)) {
                val href = hrefById[match.groupValues[1]]
                if (href == null) {
                    spineIndex += 1
                    continue
                }
                val path = resolvePath(opfPath, href)
                val html = entries[path]
                if (html != null) blocks += htmlToBlocks(html, spineIndex, path)
                spineIndex += 1
            }
            if (blocks.isEmpty()) return fail("В EPUB нет текста")
            AppResult.Ok(
                ExtractedBook(
                    title = title,
                    author = author,
                    language = primaryLanguage(rawLanguage),
                    metadata = metadata,
                    blocks = blocks,
                ),
            )
        } catch (error: Exception) {
            fail(error.message ?: "EPUB не читается")
        }
    }

    private fun fail(reason: String): AppResult<ExtractedBook> =
        AppResult.Err(DomainError.IngestionFailed(reason))
}

internal fun readZip(bytes: ByteArray): Map<String, String> {
    val entries = linkedMapOf<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) {
                val name = entry.name.replace('\\', '/').trimStart('/')
                if (!name.split('/').contains("..")) {
                    entries[name] = zip.readBytes().toString(StandardCharsets.UTF_8)
                }
            }
            zip.closeEntry()
        }
    }
    return entries
}

internal fun resolvePath(opfPath: String, href: String): String {
    val clean = href.trim().replace('\\', '/')
    if (clean.startsWith('/')) return clean.trimStart('/')
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

private fun tagText(xml: String, localName: String): String {
    val match = Regex("""<(?:[\w.-]+:)?$localName\b[^>]*>(.*?)</(?:[\w.-]+:)?$localName>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(xml) ?: return ""
    return decodeEntities(stripTags(match.groupValues[1])).trim()
}

private fun attr(attrs: String, name: String): String? =
    Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(attrs)?.groupValues?.get(1)

private fun urlDecode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

internal fun htmlToBlocks(html: String, spineIndex: Int, href: String): List<ExtractedBlock> {
    var source = html
    source = Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(source, "")
    source = Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(source, "")
    source = Regex("""<rp\b[^>]*>.*?</rp>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(source, "")
    source = Regex("""<rt\b[^>]*>.*?</rt>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).replace(source, "")
    val blocks = mutableListOf<ExtractedBlock>()
    var chapter: String? = null
    val pattern = Regex(
        """<(p|h1|h2|h3|li|blockquote)\b[^>]*>(.*?)</\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    for (match in pattern.findAll(source)) {
        val tag = match.groupValues[1].lowercase()
        val text = cleanInline(match.groupValues[2])
        if (text.isEmpty()) continue
        if (tag == "h1" || tag == "h2" || tag == "h3") chapter = text
        blocks += ExtractedBlock(spineIndex, href, chapter, text)
    }
    if (blocks.isEmpty()) {
        val body = Regex("""<body\b[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(source)?.groupValues?.get(1) ?: source
        val text = cleanInline(body)
        if (text.isNotEmpty()) blocks += ExtractedBlock(spineIndex, href, chapter, text)
    }
    return blocks
}

private fun cleanInline(html: String): String {
    val withBreaks = html
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""</?(div|span|em|strong|i|b|ruby|a)\b[^>]*>""", RegexOption.IGNORE_CASE), "")
    return collapseSpaces(decodeEntities(stripTags(withBreaks)))
}

private fun stripTags(value: String): String = value.replace(Regex("""<[^>]+>"""), "")

private fun collapseSpaces(value: String): String =
    value.replace('\u00a0', ' ').replace(Regex("""[ \t\r\n]+"""), " ").trim()

internal fun decodeEntities(value: String): String {
    val named = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
    return Regex("""&#(x?)([0-9a-fA-F]+);""").replace(named) { match ->
        val digits = match.groupValues[2]
        val code = if (match.groupValues[1].isEmpty()) digits.toInt() else digits.toInt(16)
        String(Character.toChars(code))
    }
}
