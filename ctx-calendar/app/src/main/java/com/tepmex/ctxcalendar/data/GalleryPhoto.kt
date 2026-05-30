package com.tepmex.ctxcalendar.data

import android.net.Uri
import java.time.LocalDate

data class GalleryPhoto(
    val id: Long,
    val uri: Uri,
    val date: LocalDate,
)
