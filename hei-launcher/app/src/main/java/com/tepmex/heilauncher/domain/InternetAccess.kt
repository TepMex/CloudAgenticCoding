package com.tepmex.heilauncher.domain

import java.util.Locale

/**
 * How the three probe sites answered.
 *
 * Order is ya.ru, google.com, youtube.com.
 * All three open is free. Only ya.ru is a whitelist. ya.ru and google.com
 * without youtube is the Russian network. None is offline. Anything else
 * is a foreign route.
 */
enum class InternetAccess {
    Free,
    Whitelist,
    Russian,
    Offline,
    Foreign,
}

fun classifyInternet(ya: Boolean, google: Boolean, youtube: Boolean): InternetAccess {
    return when {
        ya && google && youtube -> InternetAccess.Free
        ya && !google && !youtube -> InternetAccess.Whitelist
        ya && google && !youtube -> InternetAccess.Russian
        !ya && !google && !youtube -> InternetAccess.Offline
        else -> InternetAccess.Foreign
    }
}

fun internetLabel(access: InternetAccess, locale: Locale): String {
    val ru = locale.language == "ru"
    return when (access) {
        InternetAccess.Free -> if (ru) "СВОБОДНЫЙ" else "FREE"
        InternetAccess.Whitelist -> if (ru) "БЕЛЫЙ СПИСОК" else "WHITELIST"
        InternetAccess.Russian -> if (ru) "РОССИЙСКИЙ" else "RUSSIAN"
        InternetAccess.Offline -> if (ru) "НЕТ ИНТЕРНЕТА" else "NO INTERNET"
        InternetAccess.Foreign -> if (ru) "ИНОСТРАННЫЙ" else "FOREIGN"
    }
}
