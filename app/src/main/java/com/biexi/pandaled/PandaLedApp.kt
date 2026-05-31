package com.biexi.pandaled

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.biexi.pandaled.data.local.AppDatabase
import com.biexi.pandaled.data.local.JsonFileManager
import com.biexi.pandaled.data.repository.ProjectRepository
import com.biexi.pandaled.util.BillingManager
import java.util.Locale

class PandaLedApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var jsonFileManager: JsonFileManager
        private set

    lateinit var projectRepository: ProjectRepository
        private set

    override fun onCreate() {
        // Apply saved locale BEFORE super.onCreate() to avoid activity recreations
        val prefs = getSharedPreferences("pandaled_prefs", MODE_PRIVATE)
        val language = prefs.getString("language", "") ?: ""
        val locale = when {
            language == "zh" -> Locale("zh")
            language == "en" -> Locale("en")
            else -> {
                val sys = Locale.getDefault()
                if (sys.language.startsWith("zh")) Locale("zh") else Locale("en")
            }
        }
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.create(locale)
        )
        // Persist the resolved language so SettingsScreen and other components
        // reflect the correct currently-active locale.
        prefs.edit().putString("language", locale.language).apply()

        super.onCreate()
        instance = this

        // Initialize Google Mobile Ads
        com.google.android.gms.ads.MobileAds.initialize(this) {}

        // Initialize Google Play Billing
        BillingManager.initialize()

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
