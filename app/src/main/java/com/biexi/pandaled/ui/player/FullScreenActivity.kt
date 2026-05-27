package com.biexi.pandaled.ui.player

import android.os.Bundle
import android.util.Log
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
        Log.d("PandaFlow", "[FullScreenActivity] onCreate, savedInstanceState=${savedInstanceState != null}")

        hideSystemUi()

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: run {
            finish()
            return
        }

        setContent {
            PandaLedTheme {
                FullScreenPlayer(
                    projectId = projectId,
                    onExit = {
                        Log.d("PandaFlow", "[FullScreenActivity] onExit → finish()")
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        Log.d("PandaFlow", "[FullScreenActivity] onDestroy")
        super.onDestroy()
    }

    override fun onPause() {
        Log.d("PandaFlow", "[FullScreenActivity] onPause")
        super.onPause()
    }

    override fun onResume() {
        Log.d("PandaFlow", "[FullScreenActivity] onResume")
        super.onResume()
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
        Log.d("PandaFlow", "[FullScreenPlayer] LaunchedEffect, projectId=$projectId, 开始加载...")
        val index = repository.getProjectIndex(projectId)
        if (index != null) {
            project = repository.loadProject(index.jsonFileName)
            if (project == null) {
                Log.w("PandaFlow", "[FullScreenPlayer] 项目加载失败, onExit()")
                onExit()
            } else {
                Log.d("PandaFlow", "[FullScreenPlayer] 项目加载成功: ${project!!.name}, scenes=${project!!.scenes.size}")
            }
        } else {
            Log.w("PandaFlow", "[FullScreenPlayer] projectIndex为null, onExit()")
            onExit()
        }
    }

    if (project == null) {
        Log.d("PandaFlow", "[FullScreenPlayer] project==null, 显示加载中...")
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

    // Log state transitions
    LaunchedEffect(isFinished) {
        Log.d("PandaFlow", "[FullScreenContent] isFinished=$isFinished")
    }
    LaunchedEffect(currentIndex) {
        Log.d("PandaFlow", "[FullScreenContent] currentIndex=$currentIndex, isBeforeStartTime=$isBeforeStartTime")
    }

    Log.d("PandaFlow", "[FullScreenContent] 渲染, currentIndex=$currentIndex, isBeforeStartTime=$isBeforeStartTime, isFinished=$isFinished")

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
    Log.d("PandaFlow", "[FullScreenContent] alpha=$contentAlpha, rotation=$contentRotation")

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

    DisposableEffect(context) {
        val display = (context as android.app.Activity).windowManager.defaultDisplay
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)

        val listener = if (sensor != null) {
            val l = object : android.hardware.SensorEventListener {
                override fun onSensorChanged(event: android.hardware.SensorEvent) {
                    val x = event.values[0]
                    val displayRot = display.rotation
                    val newRotation = when (displayRot) {
                        android.view.Surface.ROTATION_90 -> if (x > 0f) 0f else 180f
                        android.view.Surface.ROTATION_270 -> if (x < 0f) 0f else 180f
                        else -> 0f
                    }
                    if (newRotation != rotation) {
                        rotation = newRotation
                        Log.d("PandaFlow", "[rememberLandscapeFlipRotation] rotation changed to $rotation")
                    }
                }

                override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(l, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
            l
        } else null

        onDispose {
            if (listener != null) {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return rotation to 1f
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
