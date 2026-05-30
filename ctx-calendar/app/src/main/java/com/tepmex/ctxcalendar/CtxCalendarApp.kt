package com.tepmex.ctxcalendar

import android.app.Application
import com.tepmex.ctxcalendar.data.PhotoRepository

class CtxCalendarApp : Application() {
    val photoRepository: PhotoRepository by lazy { PhotoRepository(this) }
}
