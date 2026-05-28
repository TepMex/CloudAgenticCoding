package com.tepmex.zuotasks.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BackupCodec {
    private const val VERSION = 1
    private val timestampFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault())

    fun encode(tree: List<TreeNodeEntity>, regular: List<RegularTaskEntity>): String =
        buildString {
            appendLine("# ZuoTasks backup v$VERSION")
            appendLine("# exported=${timestampFormatter.format(Instant.now())}")
            appendLine()
            appendLine("[tree]")
            tree.forEach { node ->
                appendLine(
                    listOf(
                        node.id,
                        node.parentId?.toString() ?: "null",
                        node.type.name,
                        escape(node.name),
                        node.isCompleted,
                        node.isHidden,
                        node.sortOrder,
                        node.subtreeTaskCount,
                        node.subtreeCompletedCount,
                    ).joinToString("|"),
                )
            }
            appendLine()
            appendLine("[regular]")
            regular.forEach { task ->
                appendLine(
                    listOf(
                        task.id,
                        escape(task.name),
                        task.lastPerformedAt?.toString() ?: "null",
                        task.sortOrder,
                    ).joinToString("|"),
                )
            }
        }

    fun decode(text: String): Pair<List<TreeNodeEntity>, List<RegularTaskEntity>> {
        val tree = mutableListOf<TreeNodeEntity>()
        val regular = mutableListOf<RegularTaskEntity>()
        var section: String? = null
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            when (line) {
                "[tree]" -> section = "tree"
                "[regular]" -> section = "regular"
                else -> when (section) {
                    "tree" -> tree += parseTreeLine(line)
                    "regular" -> regular += parseRegularLine(line)
                }
            }
        }
        return tree to regular
    }

    private fun parseTreeLine(line: String): TreeNodeEntity {
        val parts = splitEscaped(line)
        require(parts.size >= 9) { "Invalid tree line: $line" }
        return TreeNodeEntity(
            id = parts[0].toLong(),
            parentId = parts[1].let { if (it == "null") null else it.toLong() },
            type = NodeType.valueOf(parts[2]),
            name = unescape(parts[3]),
            isCompleted = parts[4].toBooleanStrict(),
            isHidden = parts[5].toBooleanStrict(),
            sortOrder = parts[6].toInt(),
            subtreeTaskCount = parts[7].toInt(),
            subtreeCompletedCount = parts[8].toInt(),
        )
    }

    private fun parseRegularLine(line: String): RegularTaskEntity {
        val parts = splitEscaped(line)
        require(parts.size >= 4) { "Invalid regular line: $line" }
        return RegularTaskEntity(
            id = parts[0].toLong(),
            name = unescape(parts[1]),
            lastPerformedAt = parts[2].let { if (it == "null") null else it.toLong() },
            sortOrder = parts[3].toInt(),
        )
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("|", "\\|")

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            if (value[i] == '\\' && i + 1 < value.length) {
                out.append(value[i + 1])
                i += 2
            } else {
                out.append(value[i])
                i++
            }
        }
        return out.toString()
    }

    private fun splitEscaped(line: String): List<String> {
        val parts = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        var escaped = false
        for (ch in line) {
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                }
                ch == '\\' -> escaped = true
                ch == '|' -> {
                    parts += current
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        parts += current
        return parts.map { it.toString() }
    }
}
