package com.tepmex.pacholli

import android.app.Application
import com.tepmex.pacholli.data.PacholliDatabase
import com.tepmex.pacholli.data.Repository

class PacholliApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(PacholliDatabase.create(this))
    }
}
