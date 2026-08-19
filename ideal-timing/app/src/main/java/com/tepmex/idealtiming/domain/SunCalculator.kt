package com.tepmex.idealtiming.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Geographic coordinates in degrees (WGS84). Longitude is positive east of Greenwich.
 */
data class GeoPoint(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
) {
    init {
        require(latitudeDeg in -90.0..90.0) { "latitude out of range: $latitudeDeg" }
        require(longitudeDeg in -180.0..180.0) { "longitude out of range: $longitudeDeg" }
    }
}

/**
 * Official sunrise / sunset for a civil calendar day in [zoneId].
 *
 * Times are the instants when the sun's center is at altitude **−0.833°**
 * (geometric horizon plus standard refraction / solar-disk correction — NOAA / USNO
 * “official” sunrise–sunset). Null when the event does not occur (polar day/night).
 */
data class SunEvents(
    val date: LocalDate,
    val zoneId: ZoneId,
    val sunrise: Instant?,
    val sunset: Instant?,
)

/**
 * Positions of sunrise / sunset on the 16-hour ideal dial (progress 0…1 from wake).
 * Null when the event falls outside `[wake, wake + 16h]` or is polar-absent.
 */
data class DialSunMarkers(
    val sunriseProgress: Float?,
    val sunsetProgress: Float?,
    val sunriseEpochSec: Long?,
    val sunsetEpochSec: Long?,
)

/**
 * Offline solar sunrise / sunset (Jean Meeus / NOAA spreadsheet approximations).
 *
 * Zenith for rise/set: `90.833°` ⇔ solar altitude `−0.833°`.
 */
object SunCalculator {
    /** Solar zenith at official sunrise/sunset (90° + 0.833°). */
    const val OFFICIAL_ZENITH_DEG = 90.833

    /** Solar altitude at official sunrise/sunset. */
    const val OFFICIAL_ALTITUDE_DEG = -0.833

    fun eventsForDate(
        date: LocalDate,
        location: GeoPoint,
        zoneId: ZoneId,
    ): SunEvents {
        val (riseMin, setMin) = utcMinutesForDate(
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth,
            latitudeDeg = location.latitudeDeg,
            longitudeEastDeg = location.longitudeDeg,
            zenithDeg = OFFICIAL_ZENITH_DEG,
        )
        val dayStartUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        fun fromUtcMinutes(minutes: Double?): Instant? =
            minutes?.let { dayStartUtc.plusMillis((it * 60_000.0).toLong()) }
        return SunEvents(
            date = date,
            zoneId = zoneId,
            sunrise = fromUtcMinutes(riseMin),
            sunset = fromUtcMinutes(setMin),
        )
    }

    /**
     * Collect official rise/set instants that fall on the 16-hour dial starting at [wakeEpochSec].
     * Events **before wake** or **after wake+16h** are omitted entirely (no icon).
     * Considers local calendar days that overlap the wake window (via [zoneId]).
     */
    fun dialMarkers(
        wakeEpochSec: Long,
        location: GeoPoint,
        zoneId: ZoneId,
        daySeconds: Long = IdealClock.DAY_SECONDS,
    ): DialSunMarkers {
        val wake = Instant.ofEpochSecond(wakeEpochSec)
        val end = Instant.ofEpochSecond(wakeEpochSec + daySeconds)
        val startDate = wake.atZone(zoneId).toLocalDate()
        val endDate = end.atZone(zoneId).toLocalDate()
        var sunrise: Instant? = null
        var sunset: Instant? = null
        var day = startDate
        while (!day.isAfter(endDate)) {
            val events = eventsForDate(day, location, zoneId)
            events.sunrise?.let { instant ->
                if (!instant.isBefore(wake) && !instant.isAfter(end)) sunrise = instant
            }
            events.sunset?.let { instant ->
                if (!instant.isBefore(wake) && !instant.isAfter(end)) sunset = instant
            }
            day = day.plusDays(1)
        }
        return DialSunMarkers(
            sunriseProgress = sunrise?.let { progressOnDial(wakeEpochSec, it.epochSecond, daySeconds) },
            sunsetProgress = sunset?.let { progressOnDial(wakeEpochSec, it.epochSecond, daySeconds) },
            sunriseEpochSec = sunrise?.epochSecond,
            sunsetEpochSec = sunset?.epochSecond,
        )
    }

    fun progressOnDial(wakeEpochSec: Long, eventEpochSec: Long, daySeconds: Long = IdealClock.DAY_SECONDS): Float? {
        val elapsed = eventEpochSec - wakeEpochSec
        if (elapsed < 0L || elapsed > daySeconds) return null
        return (elapsed.toDouble() / daySeconds.toDouble()).toFloat()
    }

    /**
     * @return pair of (sunriseUtcMinutesFromDateMidnight, sunsetUtcMinutesFromDateMidnight),
     *         either may be null for polar day/night.
     */
    internal fun utcMinutesForDate(
        year: Int,
        month: Int,
        day: Int,
        latitudeDeg: Double,
        longitudeEastDeg: Double,
        zenithDeg: Double = OFFICIAL_ZENITH_DEG,
    ): Pair<Double?, Double?> {
        val jd = julianDay(year, month, day)
        val noonApprox = solarNoonUtcMinutes(jd, longitudeEastDeg)
        val tNoon = julianCentury(jd + noonApprox / 1440.0)
        val dec0 = sunDeclinationDeg(tNoon)
        val ha0 = hourAngleSunriseDeg(latitudeDeg, dec0, zenithDeg) ?: return null to null

        var rise = noonApprox - ha0 * 4.0
        var set = noonApprox + ha0 * 4.0

        val tRise = julianCentury(jd + rise / 1440.0)
        val haRise = hourAngleSunriseDeg(latitudeDeg, sunDeclinationDeg(tRise), zenithDeg) ?: return null to null
        val noonRise = 720.0 - 4.0 * longitudeEastDeg - equationOfTimeMinutes(tRise)
        rise = noonRise - haRise * 4.0

        val tSet = julianCentury(jd + set / 1440.0)
        val haSet = hourAngleSunriseDeg(latitudeDeg, sunDeclinationDeg(tSet), zenithDeg) ?: return null to null
        val noonSet = 720.0 - 4.0 * longitudeEastDeg - equationOfTimeMinutes(tSet)
        set = noonSet + haSet * 4.0

        return rise to set
    }

    private fun julianDay(year: Int, month: Int, day: Int, hourUtc: Double = 0.0): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
        return jd + hourUtc / 24.0
    }

    private fun julianCentury(jd: Double): Double = (jd - 2451545.0) / 36525.0

    private fun geomMeanLongSunDeg(t: Double): Double {
        val l0 = 280.46646 + t * (36000.76983 + 0.0003032 * t)
        return ((l0 % 360.0) + 360.0) % 360.0
    }

    private fun geomMeanAnomSunDeg(t: Double): Double =
        357.52911 + t * (35999.05029 - 0.0001537 * t)

    private fun eccentEarthOrbit(t: Double): Double =
        0.016708634 - t * (0.000042037 + 0.0000001267 * t)

    private fun sunEqOfCenterDeg(t: Double): Double {
        val m = Math.toRadians(geomMeanAnomSunDeg(t))
        return (sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t))
            + sin(2 * m) * (0.019993 - 0.000101 * t)
            + sin(3 * m) * 0.000289)
    }

    private fun sunApparentLongDeg(t: Double): Double {
        val trueLong = geomMeanLongSunDeg(t) + sunEqOfCenterDeg(t)
        val omega = 125.04 - 1934.136 * t
        return trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
    }

    private fun meanObliquityOfEclipticDeg(t: Double): Double {
        val seconds = 21.448 - t * (46.8150 + t * (0.00059 - t * 0.001813))
        return 23.0 + (26.0 + seconds / 60.0) / 60.0
    }

    private fun obliquityCorrectionDeg(t: Double): Double {
        val e0 = meanObliquityOfEclipticDeg(t)
        val omega = 125.04 - 1934.136 * t
        return e0 + 0.00256 * cos(Math.toRadians(omega))
    }

    private fun sunDeclinationDeg(t: Double): Double {
        val e = Math.toRadians(obliquityCorrectionDeg(t))
        val lambda = Math.toRadians(sunApparentLongDeg(t))
        return Math.toDegrees(asin(sin(e) * sin(lambda)))
    }

    private fun equationOfTimeMinutes(t: Double): Double {
        val epsilon = Math.toRadians(obliquityCorrectionDeg(t))
        val l0 = Math.toRadians(geomMeanLongSunDeg(t))
        val e = eccentEarthOrbit(t)
        val m = Math.toRadians(geomMeanAnomSunDeg(t))
        val y = tan(epsilon / 2.0)
        val y2 = y * y
        val etime = (y2 * sin(2 * l0)
            - 2 * e * sin(m)
            + 4 * e * y2 * sin(m) * cos(2 * l0)
            - 0.5 * y2 * y2 * sin(4 * l0)
            - 1.25 * e * e * sin(2 * m))
        return Math.toDegrees(etime) * 4.0
    }

    private fun solarNoonUtcMinutes(jd: Double, longitudeEastDeg: Double): Double {
        val t = julianCentury(jd)
        val noonApprox = 720.0 - 4.0 * longitudeEastDeg - equationOfTimeMinutes(t)
        val t2 = julianCentury(jd + noonApprox / 1440.0)
        return 720.0 - 4.0 * longitudeEastDeg - equationOfTimeMinutes(t2)
    }

    private fun hourAngleSunriseDeg(latitudeDeg: Double, solarDecDeg: Double, zenithDeg: Double): Double? {
        val lat = Math.toRadians(latitudeDeg)
        val dec = Math.toRadians(solarDecDeg)
        val zen = Math.toRadians(zenithDeg)
        val cosH = (cos(zen) / (cos(lat) * cos(dec))) - tan(lat) * tan(dec)
        if (cosH < -1.0 || cosH > 1.0) return null
        return Math.toDegrees(acos(cosH))
    }
}
