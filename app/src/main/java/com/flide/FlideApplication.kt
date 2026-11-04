package com.flide

import android.app.Application
import com.flide.data.settings.SettingsRepository
import com.flide.env.EnvironmentManager
import com.flide.env.StorageLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FlideApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var storageLayout: StorageLayout
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var environmentManager: EnvironmentManager
        private set

    lateinit var terminalManager: com.flide.terminal.FlideTerminalManager
        private set

    lateinit var flutterManager: com.flide.flutter.FlutterManager
        private set

    lateinit var projectManager: com.flide.project.ProjectManager
        private set

    lateinit var flutterRunController: com.flide.flutter.run.FlutterRunController
        private set

    lateinit var dartLspManager: com.flide.editor.lsp.DartLspManager
        private set

    lateinit var gitManager: com.flide.git.GitManager
        private set

    lateinit var cloudBuildManager: com.flide.github.CloudBuildManager
        private set

    lateinit var storageCleaner: com.flide.data.storage.StorageCleaner
        private set

    lateinit var projectBackupManager: com.flide.project.ProjectBackupManager
        private set

    lateinit var appUpdateManager: com.flide.data.update.AppUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        storageLayout = StorageLayout(filesDir)
        storageLayout.ensureDirectoriesExist()

        settingsRepository = SettingsRepository(this)
        environmentManager = EnvironmentManager(
            context = this,
            storageLayout = storageLayout,
            settingsRepository = settingsRepository,
            coroutineScope = appScope
        )
        terminalManager = com.flide.terminal.FlideTerminalManager(
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner
        )
        flutterManager = com.flide.flutter.FlutterManager(
            context = this,
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner,
            settingsRepository = settingsRepository,
            coroutineScope = appScope
        )
        projectManager = com.flide.project.ProjectManager(
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner,
            coroutineScope = appScope
        )
        flutterRunController = com.flide.flutter.run.FlutterRunController(
            context = this,
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner,
            coroutineScope = appScope
        )
        dartLspManager = com.flide.editor.lsp.DartLspManager(
            prootRunner = environmentManager.prootRunner,
            coroutineScope = appScope
        )
        gitManager = com.flide.git.GitManager(
            prootRunner = environmentManager.prootRunner
        )
        cloudBuildManager = com.flide.github.CloudBuildManager(
            storageLayout = storageLayout,
            gitManager = gitManager,
            coroutineScope = appScope
        )
        storageCleaner = com.flide.data.storage.StorageCleaner(
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner
        )
        projectBackupManager = com.flide.project.ProjectBackupManager(
            storageLayout = storageLayout
        )
        appUpdateManager = com.flide.data.update.AppUpdateManager(
            storageLayout = storageLayout
        )
    }

    companion object {
        lateinit var instance: FlideApplication
            private set
    }
}
