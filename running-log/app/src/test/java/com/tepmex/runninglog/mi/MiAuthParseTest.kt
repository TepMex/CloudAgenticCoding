package com.tepmex.runninglog.mi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiAuthParseTest {
    @Test
    fun parseMiResponse_stripsStartMarker() {
        val json = MiAuth.parseMiResponse("""&&&START&&&{"code":0,"lp":"https://example/lp"}""")
        assertEquals(0, json.getInt("code"))
        assertEquals("https://example/lp", json.getString("lp"))
    }

    @Test
    fun parseCookieHeader_extractsPassTokenAndUserId() {
        val cookies = MiAuth.parseCookieHeader(
            "deviceId=an_abc; passToken=secret.token; userId=12345; cUserId=c_1",
        )
        assertEquals("secret.token", cookies["passToken"])
        assertEquals("12345", cookies["userId"])
        val pair = MiAuth.passTokenFromCookies(cookies)
        assertEquals("secret.token" to "12345", pair)
    }

    @Test
    fun passTokenFromCookies_requiresBoth() {
        assertNull(MiAuth.passTokenFromCookies(mapOf("passToken" to "x")))
        assertNull(MiAuth.passTokenFromCookies(mapOf("userId" to "1")))
        assertTrue(MiAuth.passTokenFromCookies(emptyMap()) == null)
    }

    @Test
    fun accountWebLoginUrl_includesSid() {
        val url = MiConstants.accountWebLoginUrl("en_US")
        assertTrue(url.contains("sid=${MiConstants.SERVICE_SID_HEALTH}"))
        assertTrue(url.contains("_locale=en_US"))
    }
}
