package com.tepmex.extensivereadingtutor

import android.app.Application

class TutorApplication : Application() {
    lateinit var graph: TutorGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = TutorGraph.create(this)
    }
}
