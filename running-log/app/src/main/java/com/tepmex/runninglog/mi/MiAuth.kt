package com.tepmex.runninglog.mi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        return store.flatMap { (domain, cookies) ->
            if (host == domain || host.endsWith(".$domain")) cookies.values else emptyList()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val domain = cookie.domain.removePrefix(".")
            store.getOrPut(domain) { ConcurrentHashMap() }[cookie.name] = cookie
        }
    }

    fun set(name: String, value: String, domain: String = "xiaomi.com") {
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain.removePrefix("."))
            .path("/")
            .build()
        store.getOrPut(domain.removePrefix(".")) { ConcurrentHashMap() }[name] = cookie
    }

    fun get(name: String): String? {
        for (cookies in store.values) {
            cookies[name]?.let { return it.value }
        }
        return null
    }

    fun getForDomain(name: String, domainHint: String): String? {
        for ((domain, cookies) in store) {
            if (domain.contains(domainHint) || domainHint.contains(domain)) {
                cookies[name]?.let { return it.value }
            }
        }
        return get(name)
    }

    fun clear() = store.clear()
}

/**
 * Xiaomi account login for Mi Fitness (miothealth SID).
 */
class MiAuth(
    private val cookieJar: MemoryCookieJar = MemoryCookieJar(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    var token: AuthToken = AuthToken()
        private set

    private var ticketToken: String = ""
    private var pendingUsername: String = ""
    private var pendingPassword: String = ""

    fun restore(token: AuthToken) {
        this.token = token
        if (token.deviceId.isNotBlank()) {
            cookieJar.set("deviceId", token.deviceId)
        }
    }

    suspend fun login(
        username: String,
        password: String,
        region: String = MiConstants.REGION_TAG_DEFAULT,
    ): AuthToken = withContext(Dispatchers.IO) {
        pendingUsername = username
        pendingPassword = password
        ensureDeviceId()
        cookieJar.set("deviceId", token.deviceId)

        val (sign, callback) = getLoginPage()
        try {
            submitPassword(username, password, sign, callback)
        } catch (_: DeviceUntrustedException) {
            // Persist region/username for SMS continuation
            token = token.copy(region = region, username = username)
            throw DeviceUntrustedException()
        }

        stsExchange()
        token = token.copy(region = region, username = username, passToken = token.passToken)
        pendingPassword = ""
        token
    }

    suspend fun sendVerificationCode(): String = withContext(Dispatchers.IO) {
        ensureTicketLoginReady()
        sendTicket()
        val (phone, ticket) = getPhoneInfo()
        ticketToken = ticket
        phone
    }

    suspend fun loginWithVerificationCode(code: String): AuthToken = withContext(Dispatchers.IO) {
        if (ticketToken.isBlank()) {
            throw AuthException("Call sendVerificationCode() first")
        }
        cookieJar.set("ticketToken", ticketToken)
        val (sign, callback) = getLoginPage(loginSign = "ticket")
        submitTicketAuth(code, sign, callback)
        stsExchange()
        token = token.copy(
            region = token.region.ifBlank { MiConstants.REGION_TAG_DEFAULT },
            username = pendingUsername.ifBlank { token.username },
        )
        ticketToken = ""
        pendingPassword = ""
        token
    }

    suspend fun refresh(): AuthToken = withContext(Dispatchers.IO) {
        if (!token.canRefresh) {
            throw TokenExpiredException("Missing passToken/userId for refresh")
        }
        ensureDeviceId()
        for ((name, value) in listOf(
            "passToken" to token.passToken,
            "deviceId" to token.deviceId,
            "userId" to token.userId,
        )) {
            cookieJar.set(name, value)
            cookieJar.set(name, value, "mi.com")
        }
        val url = MiConstants.XIAOMI_LOGIN_URL.toHttpUrl().newBuilder()
            .addQueryParameter("_json", "true")
            .addQueryParameter("sid", MiConstants.SERVICE_SID_HEALTH)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val data = parseMiResponse(resp.body?.string().orEmpty())
            val ssecurity = data.optString("ssecurity")
            if (ssecurity.isBlank()) {
                throw TokenExpiredException("passToken refresh failed: no ssecurity")
            }
            val location = data.optString("location")
            val nonceVal = data.optString("nonce")
            val cUserId = data.optString("cUserId")
            var serviceToken = ""
            if (location.isNotBlank()) {
                val signText = "nonce=$nonceVal&$ssecurity"
                val sha1 = MessageDigest.getInstance("SHA-1").digest(signText.toByteArray())
                val clientSign = java.net.URLEncoder.encode(
                    java.util.Base64.getEncoder().encodeToString(sha1),
                    Charsets.UTF_8.name(),
                )
                val fullUrl = if (location.contains("?")) {
                    "$location&clientSign=$clientSign"
                } else {
                    "$location?clientSign=$clientSign"
                }
                serviceToken = followForServiceToken(fullUrl)
            }
            token = token.copy(
                ssecurity = ssecurity,
                cUserId = cUserId.ifBlank { token.cUserId },
                serviceToken = serviceToken.ifBlank { token.serviceToken },
            )
        }
        stsExchange()
        if (!token.isAuthenticated) {
            throw TokenExpiredException("passToken refresh failed")
        }
        token
    }

    fun clear() {
        token = AuthToken()
        ticketToken = ""
        pendingPassword = ""
        pendingUsername = ""
        cookieJar.clear()
    }

    private fun ensureDeviceId() {
        if (token.deviceId.isBlank()) {
            val hex = Random.nextBytes(16).joinToString("") { "%02x".format(it) }
            token = token.copy(deviceId = "an_$hex")
        }
    }

    private fun getLoginPage(loginSign: String = ""): Pair<String, String> {
        val url = MiConstants.XIAOMI_LOGIN_URL.toHttpUrl().newBuilder()
            .addQueryParameter("_json", "true")
            .addQueryParameter("appName", MiConstants.APP_NAME)
            .addQueryParameter("sid", MiConstants.SERVICE_SID_HEALTH)
            .addQueryParameter("_locale", "zh_CN")
            .apply {
                if (loginSign.isNotBlank()) addQueryParameter("_loginSign", loginSign)
            }
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val data = parseMiResponse(body)
            return data.optString("_sign") to data.optString("callback")
        }
    }

    private fun submitPassword(username: String, password: String, sign: String, callback: String) {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .uppercase()
        val form = FormBody.Builder()
            .add("sid", MiConstants.SERVICE_SID_HEALTH)
            .add("_json", "true")
            .add("_sign", sign)
            .add("callback", callback)
            .add("user", username)
            .add("hash", md5)
            .add("qs", "%3Fsid%3D${MiConstants.SERVICE_SID_HEALTH}")
            .add("_locale", "zh_CN")
            .build()
        val req = Request.Builder()
            .url(MiConstants.XIAOMI_LOGIN_AUTH_URL)
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Referer", MiConstants.XIAOMI_LOGIN_URL)
            .post(form)
            .build()
        client.newCall(req).execute().use { resp ->
            val data = parseMiResponse(resp.body?.string().orEmpty())
            val code = data.optInt("code", -1)
            if (code == MiConstants.ERR_DEVICE_UNTRUST) {
                throw DeviceUntrustedException()
            }
            if (data.optInt("securityStatus", 0) != 0 && !data.has("ssecurity")) {
                throw DeviceUntrustedException()
            }
            if (code != 0 && !data.has("ssecurity")) {
                throw AuthException("Login failed (code=$code): ${data.optString("desc")}")
            }
            if (data.has("ssecurity")) {
                extractCredentials(data)
            } else {
                throw AuthException("Login ok but no credentials; fields=${data.keys().asSequence().toList()}")
            }
        }
    }

    private fun ensureTicketLoginReady() {
        val req = Request.Builder()
            .url(
                MiConstants.XIAOMI_PREFERENCE_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("_locale", "zh_CN")
                    .build(),
            )
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val data = parseMiResponse(resp.body?.string().orEmpty())
            if (data.optInt("code", -1) != 0) {
                throw AuthException("Preference init failed: ${data.optString("description")}")
            }
        }
    }

    private fun sendTicket(captchaCode: String = "") {
        postTicket(MiConstants.XIAOMI_SEND_TICKET_URL, captchaCode, "Send SMS failed")
    }

    private fun getPhoneInfo(captchaCode: String = ""): Pair<String, String> {
        val data = postTicket(MiConstants.XIAOMI_PHONE_INFO_URL, captchaCode, "Phone info failed")
        val info = data.optJSONObject("data") ?: JSONObject()
        val phone = info.optString("phone", "unknown")
        val ticket = info.optString("ticketToken")
        if (ticket.isBlank()) throw AuthException("No ticketToken from server")
        return phone to ticket
    }

    private fun postTicket(url: String, captchaCode: String, errorPrefix: String): JSONObject {
        val form = FormBody.Builder()
            .add("sid", MiConstants.SERVICE_SID_HEALTH)
            .add("_json", "true")
            .add("_locale", "zh_CN")
            .add("user", pendingUsername.ifBlank { token.username })
            .apply { if (captchaCode.isNotBlank()) add("captCode", captchaCode) }
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .post(form)
            .build()
        client.newCall(req).execute().use { resp ->
            val data = parseMiResponse(resp.body?.string().orEmpty())
            if (data.optInt("code", -1) == 0) return data
            val captchaUrl = data.optString("captchaUrl")
            if (captchaUrl.isNotBlank()) {
                val full = if (captchaUrl.startsWith("/")) {
                    "https://account.xiaomi.com$captchaUrl"
                } else {
                    captchaUrl
                }
                throw CaptchaRequiredException("$errorPrefix: captcha required", full)
            }
            throw AuthException("$errorPrefix: ${data.optString("description")}")
        }
    }

    private fun submitTicketAuth(code: String, sign: String, callback: String) {
        val form = FormBody.Builder()
            .add("sid", MiConstants.SERVICE_SID_HEALTH)
            .add("_json", "true")
            .add("_sign", sign)
            .add("callback", callback)
            .add("ticket", code)
            .add(
                "qs",
                "%3F_loginSign%3Dticket%26_json%3Dtrue%26sid%3D${MiConstants.SERVICE_SID_HEALTH}%26_locale%3Dzh_CN",
            )
            .add("_locale", "zh_CN")
            .build()
        val req = Request.Builder()
            .url(MiConstants.XIAOMI_TICKET_AUTH_URL)
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .header("Referer", MiConstants.XIAOMI_LOGIN_URL)
            .post(form)
            .build()
        client.newCall(req).execute().use { resp ->
            val data = parseMiResponse(resp.body?.string().orEmpty())
            val codeVal = data.optInt("code", -1)
            if (codeVal != 0) {
                throw AuthException("SMS verify failed (code=$codeVal): ${data.optString("desc")}")
            }
            if (!data.has("ssecurity")) {
                throw AuthException("SMS ok but no credentials")
            }
            extractCredentials(data)
        }
    }

    private fun extractCredentials(data: JSONObject) {
        val ssecurity = data.getString("ssecurity")
        val userId = data.optString("userId")
        val passToken = data.optString("passToken")
        val cUserId = data.optString("cUserId")
        var serviceToken = ""
        val location = data.optString("location")
        if (location.isNotBlank()) {
            serviceToken = followForServiceToken(location)
        }
        token = token.copy(
            ssecurity = ssecurity,
            userId = userId,
            passToken = passToken,
            cUserId = cUserId,
            serviceToken = serviceToken.ifBlank { token.serviceToken },
            deviceId = token.deviceId,
        )
    }

    private fun followForServiceToken(location: String): String {
        val req = Request.Builder()
            .url(location)
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            cookieJar.get("serviceToken")?.let { return it }
            val loc = resp.header("Location").orEmpty()
            if (loc.contains("serviceToken=")) {
                val url = loc.toHttpUrl()
                return url.queryParameter("serviceToken").orEmpty()
            }
            return cookieJar.get("serviceToken").orEmpty()
        }
    }

    private fun stsExchange() {
        ensureDeviceId()
        val url = MiConstants.STS_HEALTH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("d", token.deviceId)
            .addQueryParameter("ticket", "0")
            .addQueryParameter("pwd", "0")
            .addQueryParameter("p_ts", System.currentTimeMillis().toString())
            .addQueryParameter("fid", "0")
            .addQueryParameter("p_lm", "2")
            .addQueryParameter("p_ur", "CN")
            .addQueryParameter("sid", "hlth.io.mi.com")
            .build()
        if (token.userId.isNotBlank()) cookieJar.set("userId", token.userId, "mi.com")
        if (token.cUserId.isNotBlank()) cookieJar.set("cUserId", token.cUserId, "mi.com")
        if (token.passToken.isNotBlank()) cookieJar.set("passToken", token.passToken, "mi.com")
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", MiConstants.DEFAULT_LOGIN_USER_AGENT)
            .get()
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty().trim()
                if (body == "ok") {
                    val sts = cookieJar.getForDomain("serviceToken", "hlth.io.mi.com")
                        ?: cookieJar.get("serviceToken")
                    if (!sts.isNullOrBlank()) {
                        token = token.copy(serviceToken = sts)
                    }
                }
            }
        } catch (_: Exception) {
            // Non-fatal, same as Python SDK
        }
    }

    companion object {
        fun parseMiResponse(text: String): JSONObject {
            var body = text.trim()
            if (body.startsWith("&&&START&&&")) {
                body = body.removePrefix("&&&START&&&")
            }
            return try {
                JSONObject(body)
            } catch (e: Exception) {
                throw AuthException("Bad Xiaomi response: ${text.take(200)}", e)
            }
        }
    }
}
