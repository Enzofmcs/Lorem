package dev.lorem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.lorem.app.ui.LoremApp
import dev.lorem.app.ui.theme.LoremTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LoremApplication).container
        setContent {
            LoremTheme {
                LoremApp(repository = container.loremRepository)
            }
        }
    }
}
