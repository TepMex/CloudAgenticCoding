package com.tepmex.ctxcalendar.data.takeout

data class TakeoutDbInfo(
    val schemaVersion: String?,
    val eventCount: Long?,
    val builtAt: String?,
)

data class GeoTrackPoint(
    val ts: Long,
    val lat: Double,
    val lng: Double,
    val kind: String,
)

data class ChronologyVisit(
    val ts: Long,
    val tsEnd: Long?,
    val placeId: String?,
    val semanticType: String?,
    val lat: Double?,
    val lng: Double?,
)

data class ChronologyActivity(
    val ts: Long,
    val tsEnd: Long?,
    val activityType: String?,
    val distanceMeters: Double?,
    val lat: Double?,
    val lng: Double?,
)

data class YoutubeSearchEvent(
    val ts: Long,
    val query: String?,
    val url: String?,
)

data class YoutubeWatchEvent(
    val ts: Long,
    val title: String?,
    val channel: String?,
    val url: String?,
    val videoId: String?,
    val subtype: String?,
)

data class TakeoutDayTimeline(
    val track: List<GeoTrackPoint>,
    val visits: List<ChronologyVisit>,
    val activities: List<ChronologyActivity>,
    val searches: List<YoutubeSearchEvent>,
    val watches: List<YoutubeWatchEvent>,
)
