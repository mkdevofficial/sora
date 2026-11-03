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
    }

    companion object {
        lateinit var instance: FlideApplication
            private set
    }
}
