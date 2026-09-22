package dev.lorem.app

import android.app.Application

class LoremApplication : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
