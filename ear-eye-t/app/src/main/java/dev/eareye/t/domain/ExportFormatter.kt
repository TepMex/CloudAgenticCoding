package dev.eareye.t.domain

data class ExportDiagnostic(
    val wallMillis: Long,
    val kind: String,
    val message: String,
)

data class ExportDocument(
    val exportedAtMillis: Long,
    val zoneId: String,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val release: String,
    val usageAccess: Boolean,
    val listenerEnabled: Boolean,
    val listenerConnected: Boolean,
    val audioActive: Boolean,
    val screenInteractive: Boolean,
    val keyguardLocked: Boolean,
    val lastConfirmationMillis: Long?,
    val trustedUntilMillis: Long?,
    val lastSyncSuccessMillis: Long?,
    val lastSyncError: String?,
    val stats: DailyStatistics?,
    val sessions: List<SessionRecord>,
    val transitions: List<AudioTransitionRecord>,
    val screenEvents: List<ScreenEvent>,
    val diagnostics: List<ExportDiagnostic>,
)

fun exportJson(document: ExportDocument): String = buildString {
    append('{')
    field("exportedAtMillis", document.exportedAtMillis)
    field("zoneId", document.zoneId)
    field("manufacturer", document.manufacturer)
    field("model", document.model)
    field("sdkInt", document.sdkInt)
    field("release", document.release)
    field("usageAccess", document.usageAccess)
    field("listenerEnabled", document.listenerEnabled)
    field("listenerConnected", document.listenerConnected)
    field("audioActive", document.audioActive)
    field("screenInteractive", document.screenInteractive)
    field("keyguardLocked", document.keyguardLocked)
    field("lastConfirmationMillis", document.lastConfirmationMillis)
    field("trustedUntilMillis", document.trustedUntilMillis)
    field("lastSyncSuccessMillis", document.lastSyncSuccessMillis)
    field("lastSyncError", document.lastSyncError)
    append("\"stats\":")
    append(document.stats?.let(::statsJson) ?: "null")
    append(',')
    append("\"sessions\":")
    appendArray(document.sessions) { session ->
        append('{')
        field("sessionId", session.sessionId)
        field("bootCount", session.bootCount)
        field("connectedWallMillis", session.connectedWallMillis)
        field("connectedElapsedMillis", session.connectedElapsedMillis)
        field("lastConfirmedWallMillis", session.lastConfirmedWallMillis)
        field("lastConfirmedElapsedMillis", session.lastConfirmedElapsedMillis, last = true)
        append('}')
    }
    append(',')
    append("\"transitions\":")
    appendArray(document.transitions) { transition ->
        append('{')
        field("sessionId", transition.sessionId)
        field("wallMillis", transition.wallMillis)
        field("elapsedMillis", transition.elapsedMillis)
        field("bootCount", transition.bootCount)
        field("active", transition.active, last = true)
        append('}')
    }
    append(',')
    append("\"screenEvents\":")
    appendArray(document.screenEvents) { event ->
        append('{')
        field("timestampMillis", event.timestampMillis)
        field("type", event.type.name)
        field("ordinal", event.ordinal, last = true)
        append('}')
    }
    append(',')
    append("\"diagnostics\":")
    appendArray(document.diagnostics, last = true) { diagnostic ->
        append('{')
        field("wallMillis", diagnostic.wallMillis)
        field("kind", diagnostic.kind)
        field("message", diagnostic.message, last = true)
        append('}')
    }
    append('}')
}

fun exportCsv(document: ExportDocument): String = buildString {
    appendLine("# ear-eye-t diagnostics")
    appendLine("key,value")
    csvRow("exportedAtMillis", document.exportedAtMillis.toString())
    csvRow("zoneId", document.zoneId)
    csvRow("manufacturer", document.manufacturer)
    csvRow("model", document.model)
    csvRow("sdkInt", document.sdkInt.toString())
    csvRow("release", document.release)
    csvRow("usageAccess", document.usageAccess.toString())
    csvRow("listenerEnabled", document.listenerEnabled.toString())
    csvRow("listenerConnected", document.listenerConnected.toString())
    csvRow("audioActive", document.audioActive.toString())
    csvRow("screenInteractive", document.screenInteractive.toString())
    csvRow("keyguardLocked", document.keyguardLocked.toString())
    csvRow("lastConfirmationMillis", document.lastConfirmationMillis?.toString().orEmpty())
    csvRow("trustedUntilMillis", document.trustedUntilMillis?.toString().orEmpty())
    csvRow("lastSyncSuccessMillis", document.lastSyncSuccessMillis?.toString().orEmpty())
    csvRow("lastSyncError", document.lastSyncError.orEmpty())
    document.stats?.let { stats ->
        csvRow("eyeMillis", stats.eyeMillis.toString())
        csvRow("earMillis", stats.earMillis.toString())
        csvRow("overlapMillis", stats.overlapMillis.toString())
        csvRow("unionMillis", stats.unionMillis.toString())
        csvRow("eyeOnlyMillis", stats.eyeOnlyMillis.toString())
        csvRow("earOnlyMillis", stats.earOnlyMillis.toString())
        csvRow("unknownMillis", stats.unknownMillis.toString())
        csvRow("coverageFraction", stats.coverageFraction.toString())
        csvRow("incomplete", stats.incomplete.toString())
    }
    appendLine()
    appendLine("# diagnostics")
    appendLine("wallMillis,kind,message")
    document.diagnostics.forEach { row ->
        append(row.wallMillis)
        append(',')
        append(escapeCsv(row.kind))
        append(',')
        append(escapeCsv(row.message))
        appendLine()
    }
    appendLine()
    appendLine("# screen_events")
    appendLine("timestampMillis,type,ordinal")
    document.screenEvents.forEach { event ->
        append(event.timestampMillis).append(',').append(event.type.name).append(',').append(event.ordinal)
        appendLine()
    }
    appendLine()
    appendLine("# audio_transitions")
    appendLine("sessionId,wallMillis,elapsedMillis,bootCount,active")
    document.transitions.forEach { transition ->
        append(transition.sessionId).append(',')
        append(transition.wallMillis).append(',')
        append(transition.elapsedMillis).append(',')
        append(transition.bootCount).append(',')
        append(transition.active)
        appendLine()
    }
}

private fun statsJson(stats: DailyStatistics): String = buildString {
    append('{')
    field("eyeMillis", stats.eyeMillis)
    field("earMillis", stats.earMillis)
    field("eyeOnlyMillis", stats.eyeOnlyMillis)
    field("earOnlyMillis", stats.earOnlyMillis)
    field("overlapMillis", stats.overlapMillis)
    field("unionMillis", stats.unionMillis)
    field("unknownMillis", stats.unknownMillis)
    field("coverageFraction", stats.coverageFraction)
    field("incomplete", stats.incomplete, last = true)
    append('}')
}

private fun StringBuilder.field(name: String, value: String, last: Boolean = false) {
    append(jsonString(name)).append(':').append(jsonString(value))
    if (!last) append(',')
}

private fun StringBuilder.field(name: String, value: Long, last: Boolean = false) {
    append(jsonString(name)).append(':').append(value)
    if (!last) append(',')
}

private fun StringBuilder.field(name: String, value: Int, last: Boolean = false) {
    append(jsonString(name)).append(':').append(value)
    if (!last) append(',')
}

private fun StringBuilder.field(name: String, value: Boolean, last: Boolean = false) {
    append(jsonString(name)).append(':').append(if (value) "true" else "false")
    if (!last) append(',')
}

private fun StringBuilder.field(name: String, value: Double, last: Boolean = false) {
    append(jsonString(name)).append(':').append(value)
    if (!last) append(',')
}

private fun StringBuilder.field(name: String, value: Long?, last: Boolean = false) {
    append(jsonString(name)).append(':')
    append(value?.toString() ?: "null")
    if (!last) append(',')
}

@JvmName("fieldNullable")
private fun StringBuilder.field(name: String, value: String?, last: Boolean = false) {
    append(jsonString(name)).append(':')
    append(value?.let(::jsonString) ?: "null")
    if (!last) append(',')
}

private fun <T> StringBuilder.appendArray(
    items: List<T>,
    last: Boolean = false,
    body: StringBuilder.(T) -> Unit,
) {
    append('[')
    items.forEachIndexed { index, item ->
        if (index > 0) append(',')
        body(item)
    }
    append(']')
    if (!last) Unit
}

fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 0x20) append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}

private fun escapeCsv(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
}

private fun StringBuilder.csvRow(key: String, value: String) {
    append(escapeCsv(key)).append(',').append(escapeCsv(value)).appendLine()
}
