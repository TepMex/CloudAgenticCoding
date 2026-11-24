package com.tepmex.pacholli.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadAndNamesTest {
    @Test
    fun keypadRespectsPrecedence() {
        var expr = "0"
        for (key in listOf(
            PadKey.D1, PadKey.D2, PadKey.PLUS, PadKey.D3, PadKey.MUL, PadKey.D4,
        )) {
            expr = Pad.apply(expr, key)
        }
        assertEquals("12+3*4", expr)
        assertEquals(0, BigDecimal("24").compareTo(Pad.eval(expr)))
        assertEquals("12+3×4", Pad.display(expr))
        assertNull(Pad.eval("1/0"))
        assertNull(Pad.eval("12+"))
        assertEquals("0", Pad.apply("12", PadKey.AC))
        assertEquals("1", Pad.apply("12", PadKey.DEL))
        assertEquals("10.50", Pad.eval("10.5")?.let { Money.render(it) })
    }

    @Test
    fun russianNamesBecomeBeancountLeaves() {
        assertEquals("Nalichnye", Names.leafFromDisplay("Наличные"))
        assertEquals("KreditnayaKarta", Names.leafFromDisplay("Кредитная карта"))
        assertEquals("Zhile", Names.leafFromDisplay("Жильё"))
        assertEquals("Chay", Names.leafFromDisplay("Чай"))
        assertEquals(
            "Assets:Cash:Wallet",
            Names.fullName(
                AccountSection.ASSET,
                Account(
                    id = "p",
                    parentId = null,
                    name = "Наличные",
                    beancountName = "Assets:Cash",
                    type = AccountType.ACTIVE,
                    section = AccountSection.ASSET,
                    allowsSubaccounts = true,
                    cash = true,
                    openedOn = java.time.LocalDate.of(2020, 1, 1),
                    closedOn = null,
                ),
                "Wallet",
            ),
        )
    }
}
