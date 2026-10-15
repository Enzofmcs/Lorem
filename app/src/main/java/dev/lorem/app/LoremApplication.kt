package dev.lorem.app

import android.app.Application
import androidx.work.Configuration

class LoremApplication : Application(), Configuration.Provider {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        container.startSynchronization()
    }
}
