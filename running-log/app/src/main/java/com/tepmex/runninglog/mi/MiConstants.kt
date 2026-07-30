package com.tepmex.runninglog.mi

/**
 * Protocol constants for Xiaomi Fitness / Mi Health cloud.
 * Region default matches miband-bot (`ru`).
 */
object MiConstants {
    const val SERVICE_SID_HEALTH = "miothealth"
    const val APP_NAME = "com.mi.health"
    const val REGION_TAG_DEFAULT = "ru"

    const val XIAOMI_LOGIN_URL = "https://account.xiaomi.com/pass/serviceLogin"
    const val XIAOMI_LOGIN_AUTH_URL = "https://account.xiaomi.com/pass/serviceLoginAuth2"
    const val XIAOMI_PREFERENCE_URL = "https://account.xiaomi.com/pass/preference"
    const val XIAOMI_PHONE_INFO_URL = "https://account.xiaomi.com/pass/phoneInfo"
    const val XIAOMI_SEND_TICKET_URL = "https://account.xiaomi.com/pass/sendServiceLoginTicket"
    const val XIAOMI_TICKET_AUTH_URL = "https://account.xiaomi.com/pass/serviceLoginTicketAuth"
    const val XIAOMI_QR_LOGIN_URL = "https://account.xiaomi.com/longPolling/loginUrl"
    const val XIAOMI_ACCOUNT_ORIGIN = "https://account.xiaomi.com"
    const val STS_HEALTH_URL = "https://sts-hlth.io.mi.com/healthapp/sts"

    /** HTML login page (not `_json=true`) for WebView / Custom Tabs. */
    fun accountWebLoginUrl(locale: String = "en_US"): String =
        "$XIAOMI_LOGIN_URL?sid=$SERVICE_SID_HEALTH&_locale=$locale&appName=$APP_NAME"

    const val DEFAULT_USER_AGENT = "Android-12-3.53.1-vivo-V2284A"
    const val DEFAULT_LOGIN_USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 12; V2284A Build/ab8c0d1.1) " +
            "APP/mi.health APPV/353001 MK/VjIyODRB " +
            "SDKV/5.3.0.release.68 CPN/com.mi.health PassportSDK/"

    const val ERR_DEVICE_UNTRUST = 70016

    const val SPORT_RECORDS_BY_WATERMARK = "/app/v1/data/get_sport_records_by_watermark"

    val RUNNING_SPORT_KEYS = setOf("outdoor_running", "treadmill")

    fun healthApiBase(region: String): String = when (region.lowercase()) {
        "cn" -> "https://hlth.io.mi.com"
        else -> "https://ru.hlth.io.mi.com"
    }
}
