package com.biexi.pandaled.util

import com.google.gson.Gson
import com.biexi.pandaled.PandaLedApp

object DebugConfig {
    data class Config(val enableAds: Boolean? = true)

    private var _config: Config? = null

    fun enableAds(): Boolean {
        val c = _config ?: run {
            try {
                val json = PandaLedApp.instance.assets.open("debug_config.json").bufferedReader().readText()
                _config = Gson().fromJson(json, Config::class.java)
            } catch (_: Exception) {
                _config = Config(enableAds = true)
            }
            _config!!
        }
        return c.enableAds != false
    }
}
