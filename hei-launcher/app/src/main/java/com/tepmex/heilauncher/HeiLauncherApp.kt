package com.tepmex.heilauncher

import android.app.Application

class HeiLauncherApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.apps.registerPackageWatcher()
    }
}
