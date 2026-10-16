package dev.lorem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.lorem.app.ui.LoremApp

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        (application as LoremApplication).container.recoverSynchronization()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LoremApplication).container
        setContent {
            LoremApp(
                repository = container.loremRepository,
                codeforcesRepository = container.codeforcesRepository,
                synchronizationCoordinator = container.synchronizationCoordinator,
                appearanceRepository = container.appearanceRepository,
            )
        }
    }
}
