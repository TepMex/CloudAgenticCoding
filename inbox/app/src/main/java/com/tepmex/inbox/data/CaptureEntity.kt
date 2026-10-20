package com.tepmex.inbox.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tepmex.inbox.domain.Capture
import com.tepmex.inbox.domain.Place

@Entity(
    tableName = "captures",
    indices = [Index(value = ["place"])],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAtMillis: Long,
    val place: String,
    val processedAtMillis: Long?,
    val reminderAtMillis: Long?,
    val notifiedAtMillis: Long?,
)

fun CaptureEntity.toDomain(): Capture = Capture(
    id = id,
    text = text,
    createdAtMillis = createdAtMillis,
    place = Place.valueOf(place),
    processedAtMillis = processedAtMillis,
    reminderAtMillis = reminderAtMillis,
    notifiedAtMillis = notifiedAtMillis,
)

fun Capture.toEntity(): CaptureEntity = CaptureEntity(
    id = id,
    text = text,
    createdAtMillis = createdAtMillis,
    place = place.name,
    processedAtMillis = processedAtMillis,
    reminderAtMillis = reminderAtMillis,
    notifiedAtMillis = notifiedAtMillis,
)
