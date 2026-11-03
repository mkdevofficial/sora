package com.flide.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.flide.FlideApplication
import com.flide.data.settings.IdeSettings
import com.flide.ui.home.HomeScreen
import com.flide.ui.theme.FlideTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = FlideApplication.instance
        setContent {
            val settings by app.settingsRepository.settingsFlow.collectAsState(initial = IdeSettings())
            val useDark = if (settings.darkTheme) true else isSystemInDarkTheme()

            FlideTheme(darkTheme = useDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}
