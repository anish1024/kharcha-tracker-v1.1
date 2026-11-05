package com.kharcha.tracker

import android.app.Application
import com.kharcha.tracker.data.remote.NetworkModule

class KharchaApplication : Application() {
    companion object {
        lateinit var appContext: Application
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        NetworkModule.init(applicationContext)
    }
}