package com.biexi.pandaled.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.biexi.pandaled.PandaLedApp
import com.biexi.pandaled.data.model.ProjectIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PandaLedApp.instance.projectRepository

    /** All projects from Room, observed as StateFlow. */
    val projects: StateFlow<List<ProjectIndex>> = repository
        .getAllProjectIndices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun moveProject(from: ProjectIndex, to: ProjectIndex) {
        viewModelScope.launch {
            repository.moveProject(from, to)
        }
    }

    fun deleteProject(index: ProjectIndex) {
        viewModelScope.launch {
            repository.deleteProject(index)
        }
    }

    /** Create a new project from the language-appropriate template. */
    fun createNewProject(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val prefs = app.getSharedPreferences("pandaled_prefs", android.content.Context.MODE_PRIVATE)
            val lang = prefs.getString("language", "") ?: ""
            val resolved = lang.ifEmpty {
                val sys = java.util.Locale.getDefault()
                when {
                    sys.language.startsWith("zh") -> "zh"
                    sys.language.startsWith("ko") -> "ko"
                    sys.language.startsWith("ja") -> "ja"
                    sys.language.startsWith("pt") -> "pt"
                    sys.language.startsWith("es") -> "es"
                    else -> "en"
                }
            }
            val assetName = "templates/$resolved/example_project.json"
            val waitingAsset = "templates/$resolved/scene_waiting_default.json"
            val gson = com.google.gson.Gson()
            val project = runCatching {
                val json = java.io.InputStreamReader(app.assets.open(assetName)).readText()
                gson.fromJson(json, com.biexi.pandaled.data.model.Project::class.java)
            }.getOrDefault(com.biexi.pandaled.data.model.Project())
            // Load idle scene from waiting template
            val idleScene = runCatching {
                val json = java.io.InputStreamReader(app.assets.open(waitingAsset)).readText()
                gson.fromJson(json, com.biexi.pandaled.data.model.IdleScene::class.java)
            }.getOrDefault(project.idleScene)
            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val finalProject = project.copy(idleScene = idleScene, startTime = now)
            val id = repository.saveProject(finalProject)
            onCreated(id)
        }
    }
}
