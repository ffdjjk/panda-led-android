package com.biexi.pandaled.ui.player

import android.os.Bundle
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.biexi.pandaled.PandaLedApp
import com.biexi.pandaled.data.model.*
import com.biexi.pandaled.ui.detail.components.IdleSceneRenderer
import com.biexi.pandaled.ui.detail.components.SceneRenderer
import com.biexi.pandaled.ui.detail.components.TransitionOverlay
import androidx.compose.ui.res.stringResource
import com.biexi.pandaled.R
import com.biexi.pandaled.ui.theme.PandaLedTheme
import kotlinx.coroutines.delay

class FullScreenActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved locale
        val prefs = getSharedPreferences("pandaled_prefs", MODE_PRIVATE)
        val language = prefs.getString("language", "") ?: ""
        val locale = when {
            language == "zh" -> java.util.Locale("zh")
            language == "en" -> java.util.Locale("en")
            else -> {
                val sys = java.util.Locale.getDefault()
                if (sys.language.startsWith("zh")) java.util.Locale("zh") else java.util.Locale("en")
            }
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))

        hideSystemUi()

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: run {
            finish()
            return
        }

        setContent {
            PandaLedTheme {
                FullScreenPlayer(
                    projectId = projectId,
                    onExit = { finish() }
                )
            }
        }
    }

    private fun hideSystemUi() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

@Composable
fun FullScreenPlayer(
    projectId: String,
    onExit: () -> Unit
) {
    val repository = PandaLedApp.instance.projectRepository
    var project by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(projectId) {
        val index = repository.getProjectIndex(projectId)
        if (index != null) {
            project = repository.loadProject(index.jsonFileName)
            if (project == null) onExit()
        } else {
            onExit()
        }
    }

    if (project == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.fullscreen_loading), color = Color.White)
        }
        return
    }

    FullScreenContent(
        project = project!!,
        onExit = onExit
    )
}

@Composable
fun FullScreenContent(
    project: Project,
    onExit: () -> Unit
) {
    var lockShowRequest by remember { mutableIntStateOf(0) }
    var lockAlpha by remember { mutableStateOf(0.5f) }

    // Auto-hide lock after 3 seconds
    LaunchedEffect(lockShowRequest) {
        lockAlpha = 0.5f
        delay(3000)
        lockAlpha = 0f
    }

    // Scene playback state
    val initialShouldShowIdle = shouldShowIdleBeforeStart(project.startTime)
    var currentIndex by remember(project) {
        mutableStateOf(if (initialShouldShowIdle || project.scenes.isEmpty()) 0 else 1)
    }
    var isBeforeStartTime by remember(project) { mutableStateOf(initialShouldShowIdle) }
    var isFinished by remember { mutableStateOf(false) }

    LaunchedEffect(project) {
        val startMs = parseProjectStartMs(project.startTime)
        val now = System.currentTimeMillis()
        if (startMs != null && startMs > now) {
            currentIndex = 0
            isBeforeStartTime = true
            delay(startMs - now)
        }

        isBeforeStartTime = false
        if (project.scenes.isEmpty()) {
            currentIndex = 0
            return@LaunchedEffect
        }

        // Track which ONCE scenes have been consumed
        val consumedOnce = mutableSetOf<Int>()
        // Build active playlist: scenes that are either LOOP or not-yet-consumed ONCE
        fun activeScenes(): List<IndexedValue<Scene>> =
            project.scenes.withIndex().filter { (i, s) ->
                s.playMode == PlayMode.LOOP || i !in consumedOnce
            }

        if (activeScenes().isEmpty()) {
            currentIndex = 0
            return@LaunchedEffect
        }

        currentIndex = 1
        while (true) {
            val active = activeScenes()
            if (active.isEmpty()) {
                isFinished = true
                return@LaunchedEffect
            }

            // Find current scene in active list
            val currentScene = project.scenes.getOrNull(currentIndex - 1)
            val posInActive = active.indexOfFirst { it.index == currentIndex - 1 }
            if (posInActive < 0) {
                // Current scene was consumed, jump to first active
                currentIndex = active.first().index + 1
                continue
            }

            val scene = currentScene!!
            delay(scene.duration.coerceAtLeast(1) * 1000L)

            if (scene.playMode == PlayMode.ONCE) {
                consumedOnce.add(currentIndex - 1)
            }

            // Advance to next in active list
            val nextPos = (posInActive + 1) % active.size
            currentIndex = active[nextPos].index + 1
        }
    }

    val (contentRotation, contentAlpha) = rememberLandscapeFlipRotation()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { rotationZ = contentRotation; alpha = contentAlpha }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        lockShowRequest++
                    }
                )
            }
    ) {
        // ─── Content rendering ───────────────────────────
        if (isFinished) {
            // All done — black screen
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        } else if (isBeforeStartTime || currentIndex == 0) {
            IdleSceneFullRenderer(project.idleScene, project.startTime)
        } else {
            val sceneIdx = currentIndex - 1
            project.scenes.getOrNull(sceneIdx)?.let { scene ->
                key(currentIndex) {
                    SceneFullRenderer(scene)
                    FullScreenTransitionOverlay(scene)
                }
            }
        }

        // ─── Lock icon (top-left, fades after 3s) ─────────────
        if (lockAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.fullscreen_lock),
                    tint = Color.White.copy(alpha = lockAlpha),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(9.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onExit()
                                }
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun rememberLandscapeFlipRotation(): Pair<Float, Float> {
    val context = LocalContext.current
    var rotation by remember { mutableFloatStateOf(0f) }
    var rotationInitialized by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val display = (context as android.app.Activity).windowManager.defaultDisplay
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)

        if (sensor == null) {
            rotation = 0f
            rotationInitialized = true
            onDispose { }
        } else {
            val listener = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent) {
                    val x = event.values[0] // device right axis
                    val displayRot = display.rotation
                    // ROTATION_90: display top = left edge. Content upright when right edge (x>0) faces down.
                    // ROTATION_270: display top = right edge. Content upright when left edge (x<0) faces down.
                    rotation = when (displayRot) {
                        android.view.Surface.ROTATION_90 -> if (x > 0f) 0f else 180f
                        android.view.Surface.ROTATION_270 -> if (x < 0f) 0f else 180f
                        else -> 0f
                    }
                    rotationInitialized = true
                }

                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
                rotation = 0f
            }
        }
    }

    val alpha = if (rotationInitialized) 1f else 0f
    return rotation to alpha
}

// ─── Full-screen renderers ───────────────────────────────

@Composable
fun IdleSceneFullRenderer(idleScene: IdleScene, startTime: String) {
    IdleSceneRenderer(idleScene, startTime)
}

@Composable
fun SceneFullRenderer(scene: Scene) {
    SceneRenderer(scene)
}

@Composable
fun FullScreenTransitionOverlay(scene: Scene) {
    val enter = scene.transition.enter
    if (enter.type == TransitionType.NONE) return

    val bgCfg = scene.appearance.backgroundColor
    val bgColor = when (bgCfg?.type) {
        ColorType.STATIC -> bgCfg.value ?: "#0000FF"
        ColorType.TOGGLE -> bgCfg.from ?: "#0000FF"
        ColorType.GRADIENT -> bgCfg.from ?: "#0000FF"
        null -> "#0000FF"
    }
    TransitionOverlay(
        type = enter.type,
        duration = (enter.duration * 1000).toLong(),
        bgColor = bgColor
    )
}

private fun parseProjectStartMs(startTime: String): Long? {
    if (startTime.isBlank()) return null
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sdf.parse(startTime)?.time
    } catch (_: Exception) {
        null
    }
}

private fun shouldShowIdleBeforeStart(startTime: String): Boolean {
    val startMs = parseProjectStartMs(startTime) ?: return false
    return startMs > System.currentTimeMillis()
}
