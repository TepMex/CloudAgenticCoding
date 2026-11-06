package com.tepmex.cornegame.ui

import com.tepmex.cornegame.domain.HardwareStep
import com.tepmex.cornegame.domain.KeyboardModel
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.keyboardModel
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.TimeZone

/** Сколько строк держим. Старые уходят, чтобы лог можно было скопировать целиком. */
internal const val DEBUG_LOG_LIMIT = 500

/**
 * Сырой KeyEvent без android.jar: активность заполняет поля, форматтер их печатает.
 * unicodeChar — то, что событие отдало со своим metaState (это же значение идёт в сессию).
 * unicodeBare — getUnicodeChar(0), без модификаторов. Разница видна, когда Shift уже
 * вшит в символ или, наоборот, символ пустой.
 */
data class HardwareKeyTrace(
    val phase: Int,
    val keyCode: Int,
    val keyLabel: String,
    val scanCode: Int = 0,
    val unicodeChar: Int = 0,
    val unicodeBare: Int = 0,
    val metaState: Int = 0,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val ctrl: Boolean = false,
    val capsLock: Boolean = false,
    val meta: Boolean = false,
    val function: Boolean = false,
    val repeatCount: Int = 0,
    val deviceId: Int = -1,
    val deviceName: String = "-",
    val source: Int = 0,
    val flags: Int = 0,
    val characters: String? = null,
    val displayLabel: Char = '\u0000',
    val number: Char = '\u0000',
    val downTime: Long = 0,
    val eventTime: Long = 0,
)

internal fun debugStamp(epochMs: Long): String {
    val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    format.timeZone = TimeZone.getDefault()
    return format.format(Date(epochMs))
}

internal fun showChar(ch: Char?): String = when (ch) {
    null -> "-"
    ' ' -> "␣"
    '\n' -> "\\n"
    '\r' -> "\\r"
    '\t' -> "\\t"
    else -> if (ch.isISOControl()) "U+${ch.code.toString(16)}" else "'$ch'"
}

internal fun showText(text: String): String {
    if (text.isEmpty()) return "-"
    val visible = text.map { if (it == ' ') '␣' else it }.joinToString("")
    return "\"$visible\""
}

internal fun showUnicode(code: Int): String {
    if (code == 0) return "0"
    val combining = code and 0x80000000.toInt() != 0
    val base = code and 0xFFFF
    val glyph = if (base == 0) "" else showChar(base.toChar())
    return if (combining) "$code~accent$glyph" else "$code$glyph"
}

internal fun TrainerAction.traceLabel(): String = when (this) {
    is TrainerAction.Character -> "char${showChar(value)}"
    TrainerAction.Backspace -> "backspace"
    TrainerAction.Shift -> "shift"
    TrainerAction.Lower -> "L1"
    TrainerAction.Upper -> "L2"
    TrainerAction.Alt -> "alt"
    TrainerAction.AltShift -> "alt+shift"
    TrainerAction.Unexpected -> "unexpected"
}

internal fun phaseName(phase: Int): String = when (phase) {
    0 -> "down"
    1 -> "up"
    2 -> "multiple"
    else -> "phase$phase"
}

internal fun flagNames(flags: Int): String {
    if (flags == 0) return ""
    val names = listOf(
        0x1 to "WOKE",
        0x2 to "SOFT",
        0x4 to "KEEP_TOUCH",
        0x8 to "SYSTEM",
        0x10 to "EDITOR",
        0x20 to "CANCELED",
        0x40 to "VIRTUAL",
        0x80 to "LONG",
        0x100 to "CANCEL_LONG",
        0x200 to "TRACKING",
        0x400 to "FALLBACK",
    )
    return names.filter { (bit, _) -> flags and bit != 0 }.joinToString(",") { it.second }
}

internal fun formatKey(event: HardwareKeyTrace): String = buildString {
    append("KEY ")
    append(phaseName(event.phase))
    append(" code=")
    append(event.keyCode)
    append(' ')
    append(event.keyLabel)
    append(" scan=")
    append(event.scanCode)
    append(" uni=")
    append(showUnicode(event.unicodeChar))
    append(" bare=")
    append(showUnicode(event.unicodeBare))
    append(" chars=")
    append(event.characters?.let(::showText) ?: "-")
    append(" label=")
    append(showChar(event.displayLabel.takeUnless { it == '\u0000' }))
    append(" num=")
    append(showChar(event.number.takeUnless { it == '\u0000' }))
    append(" meta=0x")
    append(event.metaState.toString(16))
    append(" shift=")
    append(bit(event.shift))
    append(" alt=")
    append(bit(event.alt))
    append(" ctrl=")
    append(bit(event.ctrl))
    append(" caps=")
    append(bit(event.capsLock))
    append(" metaKey=")
    append(bit(event.meta))
    append(" fn=")
    append(bit(event.function))
    append(" rep=")
    append(event.repeatCount)
    append(" dev=")
    append(event.deviceId)
    append(" \"")
    append(event.deviceName.replace("\"", "'").replace("\n", " "))
    append("\" src=0x")
    append(event.source.toString(16))
    append(" flags=0x")
    append(event.flags.toString(16))
    val names = flagNames(event.flags)
    if (names.isNotEmpty()) {
        append(' ')
        append(names)
    }
    append(" down=")
    append(event.downTime)
    append(" t=")
    append(event.eventTime)
}

internal fun formatPending(pending: Map<Int, TrainerAction>): String =
    if (pending.isEmpty()) {
        "-"
    } else {
        pending.toSortedMap().entries.joinToString(",") { (code, action) ->
            "$code:${action.traceLabel()}"
        }
    }

internal fun formatStep(step: HardwareStep): String {
    val deliver = step.deliver?.traceLabel() ?: "-"
    return "STEP consumed=${bit(step.consumed)} deliver=$deliver " +
        "shift=${bit(step.shiftDown)} alt=${bit(step.altDown)} pending=${formatPending(step.pending)}"
}

/** Q/A/Z и знаки правой руки: по ним видно, какую раскладку отдала система. */
internal fun legendSample(table: LegendTable): String {
    if (table.isBlank()) return "legends=empty"
    val codes = listOf(
        45 to "Q",
        29 to "A",
        54 to "Z",
        74 to "SEMI",
        55 to "COMMA",
        56 to "DOT",
        76 to "SLASH",
    )
    val body = codes.joinToString(" ") { (code, name) ->
        val plain = table.plain[code]?.let(::showChar) ?: "-"
        val shifted = table.shifted[code]?.let(::showChar) ?: "-"
        "$name=$plain/$shifted"
    }
    return "legends ${table.plain.size}/${table.shifted.size} $body"
}

internal fun uiSnapshot(state: TrainUiState): String {
    val session = state.session
    val model = keyboardModel(session, state.board, state.legends)
    val board = state.board
    val typed = session.target.take(session.index.coerceIn(0, session.target.length))
    return "idx=${session.index}/${session.target.length} next=${showChar(session.nextChar)} " +
        "typed=${showText(typed)} latch=${session.latch} altArm=${bit(session.altArmed)} " +
        "err=${session.errors} nonce=${state.errorNonce} wrong=${showChar(state.lastWrong)} " +
        "screen=${state.screen} hl=${bit(state.settings.highlight)} " +
        "cap=\"${model.caption}\" hi=${marks(model, primary = true)} hint=${marks(model, primary = false)} " +
        "board=hwS${bit(board.hardwareShift)} hwA${bit(board.hardwareAlt)} " +
        "scrS${bit(board.screenShift)} scrL${bit(board.screenLower)} " +
        "scrU${bit(board.screenUpper)} scrA${bit(board.screenAlt)} " +
        "kbd=${board.language ?: "-"}"
}

internal fun uiDelta(before: TrainUiState, after: TrainUiState): String {
    val parts = mutableListOf<String>()
    if (before.session.index != after.session.index) {
        parts += "idx ${before.session.index}→${after.session.index}"
    }
    if (before.session.latch != after.session.latch) {
        parts += "latch ${before.session.latch}→${after.session.latch}"
    }
    if (before.session.altArmed != after.session.altArmed) {
        parts += "altArm ${bit(before.session.altArmed)}→${bit(after.session.altArmed)}"
    }
    if (before.session.errors != after.session.errors) {
        parts += "err ${before.session.errors}→${after.session.errors}"
    }
    if (before.errorNonce != after.errorNonce) parts += "flash"
    if (before.lastWrong != after.lastWrong) {
        parts += "wrong ${showChar(before.lastWrong)}→${showChar(after.lastWrong)}"
    }
    if (before.screen != after.screen) parts += "screen ${before.screen}→${after.screen}"
    if (before.board != after.board) parts += "board"
    if (before.legends != after.legends) parts += "legends"
    val beforeModel = keyboardModel(before.session, before.board, before.legends)
    val afterModel = keyboardModel(after.session, after.board, after.legends)
    if (beforeModel.caption != afterModel.caption) parts += "cap"
    val beforeHi = marks(beforeModel, primary = true)
    val afterHi = marks(afterModel, primary = true)
    if (beforeHi != afterHi) parts += "hi $beforeHi→$afterHi"
    val beforeHint = marks(beforeModel, primary = false)
    val afterHint = marks(afterModel, primary = false)
    if (beforeHint != afterHint) parts += "hint $beforeHint→$afterHint"
    val prefix = if (parts.isEmpty()) "same" else "Δ " + parts.joinToString(" ")
    return "$prefix | ${uiSnapshot(after)}"
}

/**
 * Свёрнутая полоса оставляет вердикт и что изменилось (индекс, подсветка, подпись),
 * а полный снимок после второй черты — только в развёрнутом логе и в копии.
 */
internal fun abbreviateDebugLine(line: String): String {
    val time = line.substringBefore(' ')
    val body = line.substringAfter(' ')
    val reaction = body.startsWith("UI ") || body.startsWith("ACTION ") || body.startsWith("TOUCH ")
    val parts = if (reaction) body.split(" | ") else emptyList()
    val short = when {
        parts.size >= 3 -> parts.take(2).joinToString(" | ")
        parts.size == 2 -> parts[0]
        body.startsWith("KEY ") -> body.take(160)
        else -> body.take(160)
    }
    return "$time $short"
}

internal fun formatUiReaction(
    before: TrainUiState,
    after: TrainUiState,
    action: TrainerAction?,
    ignored: Boolean,
    mistake: Boolean,
    moved: Boolean,
): String {
    val actionBit = when {
        action == null -> ""
        ignored -> " action=${action.traceLabel()} IGNORED"
        mistake -> " action=${action.traceLabel()} MISS expected=${showChar(before.session.nextChar)}"
        moved -> " action=${action.traceLabel()} OK"
        else -> " action=${action.traceLabel()} NOOP"
    }
    return "UI$actionBit ${uiDelta(before, after)}"
}

private fun marks(model: KeyboardModel, primary: Boolean): String =
    (model.leftRows.flatten() + model.rightRows.flatten())
        .filter { if (primary) it.primary else it.hinted }
        .joinToString(",") { face -> face.legend.ifEmpty { "#${face.id}" } }
        .ifEmpty { "-" }

private fun bit(value: Boolean): Int = if (value) 1 else 0
