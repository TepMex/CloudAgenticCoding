package com.tepmex.assumptions

import android.app.Application
import com.tepmex.assumptions.data.AssumptionsDatabase
import com.tepmex.assumptions.data.AssumptionsRepository

class AssumptionsApp : Application() {
    lateinit var repository: AssumptionsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AssumptionsRepository(AssumptionsDatabase.create(this).dao())
    }
}
