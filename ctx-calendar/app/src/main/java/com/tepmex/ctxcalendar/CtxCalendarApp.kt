package com.tepmex.ctxcalendar

import android.app.Application
import com.tepmex.ctxcalendar.data.AppPreferences
import com.tepmex.ctxcalendar.data.PhotoRepository
import com.tepmex.ctxcalendar.data.takeout.TakeoutRepository

class CtxCalendarApp : Application() {
    val photoRepository: PhotoRepository by lazy { PhotoRepository(this) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
    val takeoutRepository: TakeoutRepository by lazy { TakeoutRepository(this) }
}
