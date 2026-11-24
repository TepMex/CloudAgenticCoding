package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class PadKey {
    D0, D1, D2, D3, D4, D5, D6, D7, D8, D9,
    DOT, PLUS, MINUS, MUL, DIV, AC, DEL,
}

object Pad {
    fun apply(expr: String, key: PadKey): String {
        val current = expr.ifEmpty { "0" }
        val next = when (key) {
            PadKey.AC -> "0"
            PadKey.DEL -> backspace(current)
            PadKey.DOT -> dot(current)
            PadKey.PLUS -> operator(current, '+')
            PadKey.MINUS -> operator(current, '-')
            PadKey.MUL -> operator(current, '*')
            PadKey.DIV -> operator(current, '/')
            PadKey.D0 -> digit(current, "0")
            PadKey.D1 -> digit(current, "1")
            PadKey.D2 -> digit(current, "2")
            PadKey.D3 -> digit(current, "3")
            PadKey.D4 -> digit(current, "4")
            PadKey.D5 -> digit(current, "5")
            PadKey.D6 -> digit(current, "6")
            PadKey.D7 -> digit(current, "7")
            PadKey.D8 -> digit(current, "8")
            PadKey.D9 -> digit(current, "9")
        }
        return if (next.length > 48) current else next
    }

    fun display(expr: String): String = expr.replace('*', '×').replace('/', '÷')

    /** Arithmetic value, or null when the expression is unfinished, negative, or divides by zero. */
    fun eval(expr: String): BigDecimal? {
        if (expr.isEmpty()) return null
        val last = expr.last()
        if (last == '+' || last == '-' || last == '*' || last == '/' || last == '.') return null
        return try {
            val parser = Parser(expr)
            val value = parser.parseExpression()
            if (parser.ended() && value.signum() >= 0) Money.s(value) else null
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun backspace(expr: String): String {
        if (expr.length <= 1) return "0"
        val cut = expr.dropLast(1)
        return cut.ifEmpty { "0" }
    }

    private fun digit(expr: String, digit: String): String {
        if (expr == "0") return digit
        val number = currentNumber(expr)
        if (number == "0") return expr.dropLast(1) + digit
        return expr + digit
    }

    private fun dot(expr: String): String {
        val number = currentNumber(expr)
        if (number.contains('.')) return expr
        if (number.isEmpty()) return expr + "0."
        return "$expr."
    }

    private fun operator(expr: String, op: Char): String {
        if (expr.isEmpty() || expr == "0") return expr
        val last = expr.last()
        if (last == '.') return expr
        if (last == '+' || last == '-' || last == '*' || last == '/') return expr.dropLast(1) + op
        return expr + op
    }

    private fun currentNumber(expr: String): String {
        val cut = expr.indexOfLast { it == '+' || it == '-' || it == '*' || it == '/' }
        return if (cut < 0) expr else expr.substring(cut + 1)
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun ended(): Boolean = index == source.length

        fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (index < source.length && (source[index] == '+' || source[index] == '-')) {
                val op = source[index++]
                val right = parseTerm()
                value = if (op == '+') value.add(right) else value.subtract(right)
            }
            return value
        }

        private fun parseTerm(): BigDecimal {
            var value = parseFactor()
            while (index < source.length && (source[index] == '*' || source[index] == '/')) {
                val op = source[index++]
                val right = parseFactor()
                value = if (op == '*') {
                    value.multiply(right)
                } else {
                    if (right.signum() == 0) throw ArithmeticException("zero")
                    value.divide(right, Money.SCALE, RoundingMode.HALF_UP)
                }
            }
            return value
        }

        private fun parseFactor(): BigDecimal {
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            if (start == index) throw NumberFormatException(source)
            return BigDecimal(source.substring(start, index))
        }
    }
}
