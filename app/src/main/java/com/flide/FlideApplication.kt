package com.flide

import android.app.Application
import com.flide.data.settings.SettingsRepository
import com.flide.env.StorageLayout

class FlideApplication : Application() {

    lateinit var storageLayout: StorageLayout
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        storageLayout = StorageLayout(filesDir)
        storageLayout.ensureDirectoriesExist()

        settingsRepository = SettingsRepository(this)
    }

    companion object {
        lateinit var instance: FlideApplication
            private set
    }
}
