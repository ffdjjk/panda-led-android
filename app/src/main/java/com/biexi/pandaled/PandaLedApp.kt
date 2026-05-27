package com.biexi.pandaled

import android.app.Application
import com.biexi.pandaled.data.local.AppDatabase
import com.biexi.pandaled.data.local.JsonFileManager
import com.biexi.pandaled.data.repository.ProjectRepository
import java.util.Locale

class PandaLedApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var jsonFileManager: JsonFileManager
        private set

    lateinit var projectRepository: ProjectRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Google Mobile Ads
        com.google.android.gms.ads.MobileAds.initialize(this) {}

        database = AppDatabase.getInstance(this)
        jsonFileManager = JsonFileManager(this)
        projectRepository = ProjectRepository(
            projectDao = database.projectDao(),
            jsonFileManager = jsonFileManager
        )
    }

    companion object {
        lateinit var instance: PandaLedApp
            private set
    }
}
