package com.tepmex.zoulushang2

import android.app.Application
import com.tepmex.zoulushang2.data.AppRepository
import com.tepmex.zoulushang2.data.ZouLuShang2Database
import org.osmdroid.config.Configuration

class ZouLuShang2App : Application() {
    val database: ZouLuShang2Database by lazy { ZouLuShang2Database.get(this) }
    val repository: AppRepository by lazy { AppRepository(database) }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
    }
}
