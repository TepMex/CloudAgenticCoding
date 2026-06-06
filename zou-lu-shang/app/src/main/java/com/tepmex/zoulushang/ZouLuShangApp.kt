package com.tepmex.zoulushang

import android.app.Application
import com.tepmex.zoulushang.data.AppRepository
import com.tepmex.zoulushang.data.ZouLuShangDatabase
import org.osmdroid.config.Configuration

class ZouLuShangApp : Application() {
    val database: ZouLuShangDatabase by lazy { ZouLuShangDatabase.get(this) }
    val repository: AppRepository by lazy { AppRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
    }
}
