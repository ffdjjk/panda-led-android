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

    private val _scanResult = MutableStateFlow<String?>(null)
    val scanResult: StateFlow<String?> = _scanResult.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startScanning() {
        _isScanning.value = true
    }

    fun stopScanning() {
        _isScanning.value = false
    }

    /** Called when a QR code is successfully scanned. */
    fun onQrCodeScanned(rawText: String) {
        _isScanning.value = false
        viewModelScope.launch {
            try {
                // rawText is the base64-encoded JSON
                val json = try {
                    // Try direct JSON first, then base64 decode
                    if (rawText.startsWith("{")) rawText
                    else android.util.Base64.decode(rawText, android.util.Base64.DEFAULT).decodeToString()
                } catch (e: Exception) {
                    _importError.value = "无法解析二维码内容: ${e.message}"
                    return@launch
                }

                val project = repository.importFromJson(json)
                if (project != null) {
                    _scanResult.value = project.name
                    _importError.value = null
                } else {
                    _importError.value = "JSON 格式不正确，无法导入项目"
                }
            } catch (e: Exception) {
                _importError.value = "导入失败: ${e.message}"
            }
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
    }

    fun clearImportError() {
        _importError.value = null
    }

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
                if (java.util.Locale.getDefault().language.startsWith("zh")) "zh" else "en"
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
