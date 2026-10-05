package com.tepmex.heilauncher.domain

import java.util.Locale
import kotlin.math.roundToInt

data class Weather(val tempC: Int, val code: Int)

fun parseOpenMeteo(body: String): Weather? {
    val currentKey = body.indexOf("\"current\"")
    val slice = if (currentKey >= 0) body.substring(currentKey) else body
    val temp = TEMP_PATTERN.find(slice)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
    val code = CODE_PATTERN.find(slice)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return Weather(temp.roundToInt(), code)
}

fun weatherPhrase(code: Int, locale: Locale): String {
    val ru = locale.language == "ru"
    return when (code) {
        0 -> if (ru) "ясно" else "clear"
        1 -> if (ru) "преимущественно ясно" else "mainly clear"
        2 -> if (ru) "переменная облачность" else "partly cloudy"
        3 -> if (ru) "пасмурно" else "overcast"
        45, 48 -> if (ru) "туман" else "fog"
        51, 53, 55, 56, 57 -> if (ru) "морось" else "drizzle"
        61, 63, 65, 66, 67 -> if (ru) "дождь" else "rain"
        71, 73, 75, 77 -> if (ru) "снег" else "snow"
        80, 81, 82 -> if (ru) "ливень" else "showers"
        85, 86 -> if (ru) "снегопад" else "snow showers"
        95 -> if (ru) "гроза" else "thunderstorm"
        96, 99 -> if (ru) "гроза с градом" else "thunderstorm"
        else -> if (ru) "облачно" else "cloudy"
    }
}

fun formatWeather(weather: Weather, locale: Locale): String {
    return "${weather.tempC}°C ${weatherPhrase(weather.code, locale)}"
}

private val TEMP_PATTERN = Regex(""""temperature_2m"\s*:\s*(-?\d+(?:\.\d+)?)""")
private val CODE_PATTERN = Regex(""""weather_code"\s*:\s*(-?\d+)""")
