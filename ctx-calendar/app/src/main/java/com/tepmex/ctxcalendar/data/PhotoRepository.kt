package com.tepmex.ctxcalendar.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context) {

    suspend fun loadPhotosByDay(): Map<LocalDate, List<GalleryPhoto>> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val byDay = linkedMapOf<LocalDate, MutableList<GalleryPhoto>>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val taken = cursor.getLong(takenCol)
                val addedSeconds = cursor.getLong(addedCol)
                val millis = when {
                    taken > 0L -> taken
                    addedSeconds > 0L -> addedSeconds * 1000L
                    else -> continue
                }
                val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                val photo = GalleryPhoto(id = id, uri = uri, date = date)
                byDay.getOrPut(date) { mutableListOf() }.add(photo)
            }
        }

        byDay.mapValues { (_, photos) -> photos.sortedByDescending { it.id } }
    }

    fun findPhoto(photosByDay: Map<LocalDate, List<GalleryPhoto>>, photoId: Long): GalleryPhoto? =
        photosByDay.values.asSequence()
            .flatten()
            .firstOrNull { it.id == photoId }
}
