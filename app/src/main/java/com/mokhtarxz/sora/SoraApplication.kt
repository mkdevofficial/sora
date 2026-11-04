package com.mokhtarxz.sora

import android.app.Application
import com.mokhtarxz.sora.data.settings.SettingsRepository
import com.mokhtarxz.sora.env.EnvironmentManager
import com.mokhtarxz.sora.env.StorageLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SoraApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var storageLayout: StorageLayout
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var environmentManager: EnvironmentManager
        private set

    lateinit var terminalManager: com.mokhtarxz.sora.terminal.FlideTerminalManager
        private set

    lateinit var flutterManager: com.mokhtarxz.sora.flutter.FlutterManager
        private set

    lateinit var projectManager: com.mokhtarxz.sora.project.ProjectManager
        private set

    lateinit var flutterRunController: com.mokhtarxz.sora.flutter.run.FlutterRunController
        private set

    lateinit var dartLspManager: com.mokhtarxz.sora.editor.lsp.DartLspManager
        private set

    lateinit var gitManager: com.mokhtarxz.sora.git.GitManager
        private set

    lateinit var cloudBuildManager: com.mokhtarxz.sora.github.CloudBuildManager
        private set

    lateinit var storageCleaner: com.mokhtarxz.sora.data.storage.StorageCleaner
        private set

    lateinit var projectBackupManager: com.mokhtarxz.sora.project.ProjectBackupManager
        private set

    lateinit var appUpdateManager: com.mokhtarxz.sora.data.update.AppUpdateManager
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
        terminalManager = com.mokhtarxz.sora.terminal.FlideTerminalManager(
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner
        )
        flutterManager = com.mokhtarxz.sora.flutter.FlutterManager(
            context = this,
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner,
            settingsRepository = settingsRepository,
            coroutineScope = appScope
        )
        projectManager = com.mokhtarxz.sora.project.ProjectManager(
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner,
            coroutineScope = appScope
        )
        flutterRunController = com.mokhtarxz.sora.flutter.run.FlutterRunController(
            context = this,
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner,
            coroutineScope = appScope
        )
        dartLspManager = com.mokhtarxz.sora.editor.lsp.DartLspManager(
            prootRunner = environmentManager.prootRunner,
            coroutineScope = appScope
        )
        gitManager = com.mokhtarxz.sora.git.GitManager(
            prootRunner = environmentManager.prootRunner
        )
        cloudBuildManager = com.mokhtarxz.sora.github.CloudBuildManager(
            storageLayout = storageLayout,
            gitManager = gitManager,
            coroutineScope = appScope
        )
        storageCleaner = com.mokhtarxz.sora.data.storage.StorageCleaner(
            storageLayout = storageLayout,
            prootRunner = environmentManager.prootRunner
        )
        projectBackupManager = com.mokhtarxz.sora.project.ProjectBackupManager(
            storageLayout = storageLayout
        )
        appUpdateManager = com.mokhtarxz.sora.data.update.AppUpdateManager(
            storageLayout = storageLayout
        )
    }

    companion object {
        lateinit var instance: SoraApplication
            private set
    }
}
