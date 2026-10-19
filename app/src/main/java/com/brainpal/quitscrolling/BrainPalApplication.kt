package com.brainpal.quitscrolling

import android.app.Application
import com.brainpal.quitscrolling.core.worker.MidnightResetWorker
import com.brainpal.quitscrolling.di.AppContainer

class BrainPalApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        MidnightResetWorker.schedule(this)
    }

    companion object {
        @Volatile
        var instance: BrainPalApplication? = null
            private set
    }
}
