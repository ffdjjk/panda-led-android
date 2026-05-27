package com.biexi.pandaled.ui.detail

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biexi.pandaled.PandaLedApp
import com.biexi.pandaled.data.model.*
import com.biexi.pandaled.ui.player.FullScreenActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which part of the project is currently selected for editing / preview.
 */
sealed class EditorTarget {
    /** Nothing is selected. */
    data object None : EditorTarget()
    /** idleScene is selected. */
    data object IdleScene : EditorTarget()
    /** A specific scene by index. */
    data class Scene(val index: Int) : EditorTarget()
}

data class DetailUiState(
    val project: Project? = null,
    val projectIndex: ProjectIndex? = null,
    val isLoading: Boolean = true,
    val selectedTarget: EditorTarget = EditorTarget.None,
    val isPreviewPlaying: Boolean = false,
    val previewReplayKey: Int = 0,
    val previewCurrentIndex: Int = 0,     // which scene index is currently playing in preview
    val previewTotalCount: Int = 0,
    val qrCodeBitmap: android.graphics.Bitmap? = null,
    val showQrDialog: Boolean = false,
    val missingAssets: List<MissingAsset> = emptyList(),
    val highlightedSceneIndex: Int? = null, // for "待补充完整" click highlight
    val tabToSwitch: Int? = null  // trigger tab switch from outside
)

class DetailViewModel : ViewModel() {

    private val repository = PandaLedApp.instance.projectRepository

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var currentProject: Project? = null
    private var currentIndex: ProjectIndex? = null

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val index = repository.getProjectIndex(projectId)
            currentIndex = index
            if (index != null) {
                val project = repository.loadProject(index.jsonFileName)
                currentProject = project
                val missing = project?.findMissingAssets() ?: emptyList()
                val firstSceneIndex = if (project != null && project.scenes.isNotEmpty()) 0 else null
                _uiState.value = _uiState.value.copy(
                    project = project,
                    projectIndex = index,
                    isLoading = false,
                    missingAssets = missing,
                    selectedTarget = if (firstSceneIndex != null) EditorTarget.Scene(firstSceneIndex) else EditorTarget.IdleScene,
                    previewTotalCount = (project?.scenes?.size ?: 0) + 1 // +1 for idleScene
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // ─── Selection ───────────────────────────────────────

    fun selectIdleScene() {
        _uiState.value = _uiState.value.copy(
            selectedTarget = EditorTarget.IdleScene,
            highlightedSceneIndex = null
        )
    }

    fun selectScene(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectedTarget = EditorTarget.Scene(index),
            highlightedSceneIndex = null
        )
    }

    // ─── Scene reordering ────────────────────────────────

    fun moveScene(fromIndex: Int, toIndex: Int) {
        val project = currentProject ?: return
        val mutableScenes = project.scenes.toMutableList()
        val item = mutableScenes.removeAt(fromIndex)
        mutableScenes.add(toIndex, item)
        currentProject = project.copy(scenes = mutableScenes)
        _uiState.value = _uiState.value.copy(project = currentProject)

        // Adjust selection if needed
        val currentTarget = _uiState.value.selectedTarget
        if (currentTarget is EditorTarget.Scene) {
            val idx = currentTarget.index
            val newIdx = when {
                idx == fromIndex -> toIndex
                idx in (fromIndex + 1)..toIndex -> idx - 1
                idx in toIndex..<fromIndex -> idx + 1
                else -> idx
            }
            _uiState.value = _uiState.value.copy(
                selectedTarget = EditorTarget.Scene(newIdx)
            )
        }
        autoSave()
    }

    // ─── Editing ─────────────────────────────────────────

    fun updateIdleScene(idleScene: IdleScene) {
        val project = currentProject ?: return
        currentProject = project.copy(idleScene = idleScene)
        refreshState()
        autoSave()
    }

    fun updateScene(index: Int, scene: Scene) {
        val project = currentProject ?: return
        val mutableScenes = project.scenes.toMutableList()
        mutableScenes[index] = scene
        currentProject = project.copy(scenes = mutableScenes)
        refreshState()
        // Re-trigger preview playback for this scene
        triggerScenePreview(index)
        autoSave()
    }

    fun updateProjectInfo(name: String, startTime: String) {
        val project = currentProject ?: return
        currentProject = project.copy(name = name, startTime = startTime)
        refreshState()
        autoSave()
    }

    fun addScene(type: SceneType = SceneType.TEXT) {
        val project = currentProject ?: return
        val prefs = PandaLedApp.instance.getSharedPreferences("pandaled_prefs", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "") ?: ""
        val resolved = lang.ifEmpty {
            if (java.util.Locale.getDefault().language.startsWith("zh")) "zh" else "en"
        }
        val isZh = resolved == "zh"
        val typeLabel = when (type) {
            SceneType.TEXT -> if (isZh) "文字" else "Text"
            SceneType.IMAGE -> if (isZh) "图片" else "Image"
            SceneType.VIDEO -> if (isZh) "视频" else "Video"
        }
        val sceneFile = when (type) {
            SceneType.TEXT -> "scene_text_default.json"
            SceneType.IMAGE -> "scene_image_default.json"
            SceneType.VIDEO -> "scene_video_default.json"
        }
        val templateScene = runCatching {
            val json = PandaLedApp.instance.assets.open("templates/$resolved/$sceneFile")
                .bufferedReader().readText()
            com.google.gson.Gson().fromJson(json, Scene::class.java)
        }.getOrDefault(Scene(type = type, playMode = PlayMode.LOOP))
        val newScene = templateScene.copy(name = "$typeLabel ${project.scenes.size + 1}")
        val newIndex = project.scenes.size
        currentProject = project.copy(scenes = project.scenes + newScene)
        _uiState.value = _uiState.value.copy(
            selectedTarget = EditorTarget.Scene(newIndex)
        )
        refreshState()
        autoSave()
    }

    fun deleteScene(index: Int) {
        val project = currentProject ?: return
        val mutableScenes = project.scenes.toMutableList()
        if (index in mutableScenes.indices) {
            mutableScenes.removeAt(index)
            currentProject = project.copy(scenes = mutableScenes)

            // Adjust selection
            val sel = _uiState.value.selectedTarget
            val newSel = if (sel is EditorTarget.Scene) {
                if (sel.index == index) {
                    // Was viewing deleted scene → go to idle or adjacent
                    if (mutableScenes.isEmpty()) EditorTarget.IdleScene
                    else EditorTarget.Scene(index.coerceAtMost(mutableScenes.lastIndex))
                } else if (sel.index > index) {
                    EditorTarget.Scene(sel.index - 1)
                } else sel
            } else sel

            refreshState()
            _uiState.value = _uiState.value.copy(selectedTarget = newSel)
            autoSave()
        }
    }

    // ─── Preview playback ────────────────────────────────

    fun startPreviewPlayback() {
        val current = _uiState.value.selectedTarget
        val startIdx = when (current) {
            is EditorTarget.IdleScene -> 0
            is EditorTarget.Scene -> current.index + 1
            is EditorTarget.None -> 0
        }
        _uiState.value = _uiState.value.copy(
            isPreviewPlaying = true,
            previewCurrentIndex = startIdx
        )
    }

    fun advancePreview() {
        val state = _uiState.value
        val project = currentProject ?: return
        val totalScenes = project.scenes.size
        val next = state.previewCurrentIndex + 1
        if (next > totalScenes) {
            // Finished all scenes (including idleScene)
            _uiState.value = state.copy(isPreviewPlaying = false, previewCurrentIndex = 0)
        } else {
            _uiState.value = state.copy(previewCurrentIndex = next)
        }
    }

    fun stopPreview() {
        _uiState.value = _uiState.value.copy(isPreviewPlaying = false, previewCurrentIndex = 0)
    }

    /** Re-show scene in preview on update. */
    fun triggerScenePreview(sceneIndex: Int) {
        _uiState.value = _uiState.value.copy(
            selectedTarget = EditorTarget.Scene(sceneIndex),
            isPreviewPlaying = true,
            previewReplayKey = _uiState.value.previewReplayKey + 1
        )
    }

    /** Re-show current scene in preview. */
    fun triggerScenePreview(target: EditorTarget) {
        _uiState.value = _uiState.value.copy(
            isPreviewPlaying = true,
            previewReplayKey = _uiState.value.previewReplayKey + 1
        )
    }

    // ─── Save ────────────────────────────────────────────

    fun saveProject() {
        val project = currentProject ?: return
        val index = currentIndex ?: return
        viewModelScope.launch {
            repository.updateProject(index, project)
            // Refresh missing assets
            val missing = project.findMissingAssets()
            _uiState.value = _uiState.value.copy(missingAssets = missing)
        }
    }

    // ─── Missing asset navigation ────────────────────────

    fun navigateToMissingAsset(missing: MissingAsset) {
        if (missing.isIdle) {
            _uiState.value = _uiState.value.copy(
                selectedTarget = EditorTarget.IdleScene,
                highlightedSceneIndex = null
            )
        } else {
            missing.sceneIndex?.let { idx ->
                _uiState.value = _uiState.value.copy(
                    selectedTarget = EditorTarget.Scene(idx),
                    highlightedSceneIndex = idx
                )
            }
        }
    }

    // ─── Full screen ─────────────────────────────────────

    fun launchFullScreen(context: Context) {
        val index = currentIndex ?: return
        val project = currentProject ?: return
        viewModelScope.launch {
            repository.updateProject(index, project)
            val intent = Intent(context, FullScreenActivity::class.java).apply {
                putExtra(FullScreenActivity.EXTRA_PROJECT_ID, index.id)
            }
            context.startActivity(intent)
        }
    }

    // ─── QR share ────────────────────────────────────────

    fun generateShareQr(label: String) {
        val project = currentProject ?: return
        val json = repository.projectToJson(project)
        val base64 = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val bitmap = com.biexi.pandaled.util.QrCodeHelper.generateQrCodeWithLabel(base64, 512, label)
        _uiState.value = _uiState.value.copy(
            qrCodeBitmap = bitmap,
            showQrDialog = true
        )
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(tabToSwitch = index)
    }

    fun clearTabSwitch() {
        _uiState.value = _uiState.value.copy(tabToSwitch = null)
    }

    fun dismissQrDialog() {
        _uiState.value = _uiState.value.copy(showQrDialog = false, qrCodeBitmap = null)
    }

    // ─── Helpers ─────────────────────────────────────────

    private fun autoSave() {
        viewModelScope.launch {
            saveProject()
        }
    }

    private fun refreshState() {
        val project = currentProject
        _uiState.value = _uiState.value.copy(
            project = project,
            missingAssets = project?.findMissingAssets() ?: emptyList(),
            previewTotalCount = (project?.scenes?.size ?: 0) + 1
        )
    }

    /** Get the currently selected scene (or null if idleScene is selected). */
    fun getSelectedScene(): Scene? {
        val target = _uiState.value.selectedTarget
        val project = currentProject ?: return null
        return when (target) {
            is EditorTarget.Scene -> project.scenes.getOrNull(target.index)
            is EditorTarget.IdleScene -> null
            is EditorTarget.None -> null
        }
    }

    fun getSelectedSceneIndex(): Int? {
        return when (val target = _uiState.value.selectedTarget) {
            is EditorTarget.Scene -> target.index
            is EditorTarget.IdleScene -> null
            is EditorTarget.None -> null
        }
    }
}
