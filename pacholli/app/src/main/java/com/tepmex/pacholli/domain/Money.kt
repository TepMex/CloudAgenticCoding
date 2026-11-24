package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    const val SCALE = 8
    private val ROUND = RoundingMode.HALF_UP
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(SCALE)
    private val DECIMAL = Regex("""-?\d{1,3}(,\d{3})+(\.\d+)?|-?\d+(\.\d+)?""")

    fun s(value: BigDecimal): BigDecimal = value.setScale(SCALE, ROUND)

    fun eq(a: BigDecimal, b: BigDecimal): Boolean = s(a).compareTo(s(b)) == 0

    fun parse(raw: String): BigDecimal? {
        val trimmed = raw.trim().replace(" ", "").replace("\u00A0", "")
        if (!trimmed.matches(DECIMAL)) return null
        return s(BigDecimal(trimmed.replace(",", "")))
    }

    /** Accepts 90.5, 90,5 and 1 000.50 from a text field. */
    fun parseUser(raw: String): BigDecimal? {
        var trimmed = raw.trim().replace(" ", "").replace("\u00A0", "")
        if (trimmed.isEmpty()) return null
        if (',' in trimmed && '.' !in trimmed) trimmed = trimmed.replace(',', '.')
        else trimmed = trimmed.replace(",", "")
        return try {
            s(BigDecimal(trimmed))
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Canonical Beancount amount: at least two decimal places, no exponent. */
    fun render(value: BigDecimal): String {
        val scaled = s(value).stripTrailingZeros()
        val plain = if (scaled.scale() < 0) scaled.setScale(0) else scaled
        val withCents = if (plain.scale() < 2) plain.setScale(2) else plain
        return withCents.toPlainString()
    }

    fun display(value: BigDecimal, fraction: Int = 2): String {
        val symbols = DecimalFormatSymbols(Locale("ru", "RU"))
        val pattern = if (fraction <= 0) "#,##0" else "#,##0." + "0".repeat(fraction)
        val format = DecimalFormat(pattern, symbols)
        format.roundingMode = ROUND
        return format.format(s(value))
    }

    fun displayQty(value: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale("ru", "RU"))
        val format = DecimalFormat("#,##0.########", symbols)
        format.roundingMode = ROUND
        return format.format(s(value).stripTrailingZeros())
    }
}
