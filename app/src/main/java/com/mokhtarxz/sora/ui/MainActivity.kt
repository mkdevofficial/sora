package com.mokhtarxz.sora.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mokhtarxz.sora.SoraApplication
import com.mokhtarxz.sora.data.settings.IdeSettings
import com.mokhtarxz.sora.ui.env.SetupEnvironmentScreen
import com.mokhtarxz.sora.ui.flutter.SetupFlutterScreen
import com.mokhtarxz.sora.ui.home.HomeScreen
import com.mokhtarxz.sora.ui.ide.IdeScreen
import com.mokhtarxz.sora.ui.terminal.TerminalScreen
import com.mokhtarxz.sora.ui.theme.FlideTheme

enum class Screen {
    Home,
    SetupEnvironment,
    Terminal,
    SetupFlutter,
    Ide
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure background service with partial wake lock is active
        com.mokhtarxz.sora.service.IdeService.start(this, "Flide IDE active in background")

        val app = SoraApplication.instance
        setContent {
            val settings by app.settingsRepository.settingsFlow.collectAsState(initial = IdeSettings())
            val useDark = if (settings.darkTheme) true else isSystemInDarkTheme()

            var currentScreen by rememberSaveable { mutableStateOf(Screen.Home) }
            var terminalWorkingDir by rememberSaveable { mutableStateOf("/home/flide") }
            var terminalInitialCommand by rememberSaveable { mutableStateOf<String?>(null) }

            FlideTheme(darkTheme = useDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.Home -> {
                            HomeScreen(
                                onNavigateToSetup = { currentScreen = Screen.SetupEnvironment },
                                onNavigateToTerminal = {
                                    terminalWorkingDir = "/home/flide"
                                    terminalInitialCommand = null
                                    currentScreen = Screen.Terminal
                                },
                                onNavigateToFlutterSetup = { currentScreen = Screen.SetupFlutter },
                                onOpenProject = { project ->
                                    app.projectManager.selectProject(project)
                                    currentScreen = Screen.Ide
                                },
                                onOpenInTerminalWithCommand = { workingDir, command ->
                                    terminalWorkingDir = workingDir
                                    terminalInitialCommand = command
                                    currentScreen = Screen.Terminal
                                }
                            )
                        }
                        Screen.SetupEnvironment -> {
                            BackHandler {
                                currentScreen = Screen.Home
                            }
                            SetupEnvironmentScreen(
                                onNavigateBack = { currentScreen = Screen.Home },
                                onNavigateToTerminal = {
                                    terminalWorkingDir = "/home/flide"
                                    terminalInitialCommand = null
                                    currentScreen = Screen.Terminal
                                }
                            )
                        }
                        Screen.Terminal -> {
                            BackHandler {
                                currentScreen = Screen.Home
                            }
                            TerminalScreen(
                                onNavigateBack = { currentScreen = Screen.Home },
                                initialWorkingDir = terminalWorkingDir,
                                initialCommand = terminalInitialCommand
                            )
                        }
                        Screen.SetupFlutter -> {
                            BackHandler {
                                currentScreen = Screen.Home
                            }
                            SetupFlutterScreen(
                                onNavigateBack = { currentScreen = Screen.Home },
                                onNavigateToTerminal = {
                                    terminalWorkingDir = "/home/flide"
                                    terminalInitialCommand = null
                                    currentScreen = Screen.Terminal
                                }
                            )
                        }
                        Screen.Ide -> {
                            val activeProject by app.projectManager.activeProject.collectAsState()
                            BackHandler {
                                currentScreen = Screen.Home
                            }
                            activeProject?.let { proj ->
                                IdeScreen(
                                    project = proj,
                                    onNavigateBack = { currentScreen = Screen.Home }
                                )
                            } ?: run {
                                currentScreen = Screen.Home
                            }
                        }
                    }
                }
            }
        }
    }
}
