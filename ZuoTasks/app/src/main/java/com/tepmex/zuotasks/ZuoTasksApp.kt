package com.tepmex.zuotasks

import android.app.Application
import com.tepmex.zuotasks.data.ZuoTasksDatabase
import com.tepmex.zuotasks.data.ZuoTasksRepository

class ZuoTasksApp : Application() {
    val repository: ZuoTasksRepository by lazy {
        val db = ZuoTasksDatabase.get(this)
        ZuoTasksRepository(db.treeNodeDao(), db.regularTaskDao())
    }
}
