package com.tepmex.heilauncher.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class InternetAccessTest {
    @Test
    fun everySiteCombinationMapsToOneStatus() {
        assertEquals(InternetAccess.Free, classifyInternet(ya = true, google = true, youtube = true))
        assertEquals(InternetAccess.Whitelist, classifyInternet(ya = true, google = false, youtube = false))
        assertEquals(InternetAccess.Russian, classifyInternet(ya = true, google = true, youtube = false))
        assertEquals(InternetAccess.Offline, classifyInternet(ya = false, google = false, youtube = false))
        assertEquals(InternetAccess.Foreign, classifyInternet(ya = false, google = true, youtube = false))
        assertEquals(InternetAccess.Foreign, classifyInternet(ya = false, google = false, youtube = true))
        assertEquals(InternetAccess.Foreign, classifyInternet(ya = false, google = true, youtube = true))
        assertEquals(InternetAccess.Foreign, classifyInternet(ya = true, google = false, youtube = true))
    }

    @Test
    fun labelsUseTheRussianStatusNames() {
        val ru = Locale.forLanguageTag("ru")
        assertEquals("СВОБОДНЫЙ", internetLabel(InternetAccess.Free, ru))
        assertEquals("БЕЛЫЙ СПИСОК", internetLabel(InternetAccess.Whitelist, ru))
        assertEquals("РОССИЙСКИЙ", internetLabel(InternetAccess.Russian, ru))
        assertEquals("НЕТ ИНТЕРНЕТА", internetLabel(InternetAccess.Offline, ru))
        assertEquals("ИНОСТРАННЫЙ", internetLabel(InternetAccess.Foreign, ru))
    }

    @Test
    fun labelsUseEnglishStatusNames() {
        val en = Locale.ENGLISH
        assertEquals("FREE", internetLabel(InternetAccess.Free, en))
        assertEquals("WHITELIST", internetLabel(InternetAccess.Whitelist, en))
        assertEquals("RUSSIAN", internetLabel(InternetAccess.Russian, en))
        assertEquals("NO INTERNET", internetLabel(InternetAccess.Offline, en))
        assertEquals("FOREIGN", internetLabel(InternetAccess.Foreign, en))
    }
}
