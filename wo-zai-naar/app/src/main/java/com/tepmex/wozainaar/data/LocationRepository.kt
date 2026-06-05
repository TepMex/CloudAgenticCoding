package com.tepmex.wozainaar.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class LocationRepository(
    private val dao: LocationPointDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun observeDay(date: LocalDate): Flow<List<LocationPoint>> {
        val (start, end) = dayBounds(date)
        return dao.observeForDay(start, end)
    }

    suspend fun insertSample(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        recordedAt: Long = System.currentTimeMillis(),
    ): Long = dao.insert(
        LocationPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            recordedAt = recordedAt,
        ),
    )

    suspend fun countAll(): Int = dao.countAll()

    private fun dayBounds(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }
}
