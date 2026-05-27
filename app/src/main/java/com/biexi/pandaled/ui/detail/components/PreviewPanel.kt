package com.biexi.pandaled.ui.detail.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.biexi.pandaled.R
import com.biexi.pandaled.data.model.*
import kotlinx.coroutines.delay

/**
 * LED-style preview panel that renders the current scene / idleScene.
 *
 * - idleScene: shows clock/countdown text, image, or black screen
 * - text scene: renders text with scroll/mirror/blink
 * - image scene: loads local image
 * - video scene: placeholder (video in ExoPlayer is complex; show play icon)
 */
@Composable
fun PreviewPanel(
    project: Project,
    currentPreviewIndex: Int,
    isPlaying: Boolean,
    replayKey: Int = 0,
    sceneName: String = "",
    selectedTarget: com.biexi.pandaled.ui.detail.EditorTarget,
    onAutoAdvance: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Always show selected scene; isPlaying only controls transition overlay
    val displayScene: Scene? = when (selectedTarget) {
        is com.biexi.pandaled.ui.detail.EditorTarget.Scene ->
            project.scenes.getOrNull(selectedTarget.index)
        else -> null
    }

    val displayIdleScene: IdleScene? = when (selectedTarget) {
        is com.biexi.pandaled.ui.detail.EditorTarget.IdleScene -> project.idleScene
        else -> null
    }

    val totalCount = project.scenes.size + 1  // +1 for idleScene
    val currentLabel = when (selectedTarget) {
        is com.biexi.pandaled.ui.detail.EditorTarget.IdleScene -> "1"
        is com.biexi.pandaled.ui.detail.EditorTarget.Scene -> "${selectedTarget.index + 2}"
        is com.biexi.pandaled.ui.detail.EditorTarget.None -> "-"
    }

    Box(
        modifier = modifier
            .background(Color(0xFF111111))
    ) {
        // Render the scene/idle content
        when {
            displayIdleScene != null -> IdleSceneRenderer(displayIdleScene, project.startTime)
            displayScene != null -> SceneRenderer(displayScene, isPreview = true, replayKey = replayKey)
            else -> {
                // Black / empty
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        // Scene name overlay (top-left)
        if (sceneName.isNotBlank()) {
            Text(
                text = sceneName,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Duration countdown overlay (bottom-left)
        if (displayScene != null) {
            val dur = displayScene.duration.coerceAtLeast(1)
            var countdown by remember { mutableIntStateOf(dur) }
            LaunchedEffect(replayKey, displayScene) {
                countdown = dur
                while (countdown > 0) {
                    kotlinx.coroutines.delay(1000L)
                    countdown--
                }
                onAutoAdvance()
            }
            Text(
                text = stringResource(R.string.countdown_sec, countdown),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Scene counter overlay (bottom-left)
        Text(
            text = "$currentLabel / ${project.scenes.size + 1}",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )



        // Transition effect overlay (if applicable)
        val transitionType = displayScene?.transition?.enter?.type
        val transitionDuration = ((displayScene?.transition?.enter?.duration ?: 0f) * 1000).toLong()
        val bgCfg = displayScene?.appearance?.backgroundColor
        val transitionBgColor = when (bgCfg?.type) {
            ColorType.STATIC -> bgCfg.value ?: "#0000FF"
            ColorType.TOGGLE -> bgCfg.from ?: "#0000FF"
            ColorType.GRADIENT -> bgCfg.from ?: "#0000FF"
            else -> "#0000FF"
        }
        if (transitionType != null && transitionType != TransitionType.NONE && isPlaying) {
            TransitionOverlay(
                type = transitionType,
                duration = transitionDuration,
                bgColor = transitionBgColor,
                replayKey = replayKey
            )
        }
    }
}

// ─── Idle Scene Renderer ─────────────────────────────────

@Composable
fun IdleSceneRenderer(idleScene: IdleScene, startTime: String) {
    val style = idleScene.content?.style
    when (idleScene.type) {
        IdleSceneType.NONE -> {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        IdleSceneType.CLOCK -> {
            ClockRenderer(
                format = style?.format ?: "{hours}:{minutes}:{seconds}",
                fontKey = style?.fontFamily,
                configuredSize = style?.size ?: 40,
                colorHex = style?.color ?: "#FFFFFF"
            )
        }
        IdleSceneType.COUNTDOWN -> {
            CountdownRenderer(
                format = style?.format ?: "{hours}:{minutes}:{seconds}",
                startTime = startTime,
                fontKey = style?.fontFamily,
                configuredSize = style?.size ?: 40,
                colorHex = style?.color ?: "#FFFFFF"
            )
        }
        IdleSceneType.IMAGE -> {
            val source = idleScene.content?.source
            if (!source.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(source)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(com.biexi.pandaled.R.string.idle_image),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Missing image placeholder
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(com.biexi.pandaled.R.string.editor_missing_image), color = Color.Red, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun ClockRenderer(format: String, fontKey: String? = null, configuredSize: Int = 40, colorHex: String = "#FFFFFF") {
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = java.util.Calendar.getInstance()
            val h = now.get(java.util.Calendar.HOUR_OF_DAY)
            val m = now.get(java.util.Calendar.MINUTE)
            val s = now.get(java.util.Calendar.SECOND)
            timeText = format
                .replace("{hours}", h.toString().padStart(2, '0'))
                .replace("{minutes}", m.toString().padStart(2, '0'))
                .replace("{seconds}", s.toString().padStart(2, '0'))
            delay(1000)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val viewShort = size.width.coerceAtMost(size.height)
        val paint = android.graphics.Paint().apply {
            color = try { android.graphics.Color.parseColor(colorHex) } catch (_: Exception) { -1 }
            textSize = relativeTextSizePx(configuredSize, viewShort)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = typefaceForKey(fontKey)
        }
        val y = center.y - (paint.descent() + paint.ascent()) / 2
        drawContext.canvas.nativeCanvas.drawText(
            timeText, center.x, y, paint
        )
    }
}

@Composable
fun CountdownRenderer(format: String, startTime: String, fontKey: String? = null, configuredSize: Int = 40, colorHex: String = "#FF8800") {
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()) }
    val targetMs = remember(startTime) {
        try { sdf.parse(startTime)?.time ?: 0L } catch (_: Exception) { 0L }
    }

    var remainingMs by remember { mutableStateOf(targetMs - System.currentTimeMillis()) }

    LaunchedEffect(targetMs) {
        while (true) {
            remainingMs = targetMs - System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val displayText = if (remainingMs <= 0) {
        format
            .replace("{hours}", "00")
            .replace("{minutes}", "00")
            .replace("{seconds}", "00")
    } else {
        val totalSec = remainingMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        format
            .replace("{hours}", h.toString().padStart(2, '0'))
            .replace("{minutes}", m.toString().padStart(2, '0'))
            .replace("{seconds}", s.toString().padStart(2, '0'))
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val paint = android.graphics.Paint().apply {
            color = try { android.graphics.Color.parseColor(colorHex) } catch (_: Exception) { -1 }
            textSize = relativeTextSizePx(configuredSize, minOf(size.width, size.height))
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = typefaceForKey(fontKey)
        }
        val y = center.y - (paint.descent() + paint.ascent()) / 2
        drawContext.canvas.nativeCanvas.drawText(
            displayText, center.x, y, paint
        )
    }
}

// ─── Scene Renderer ──────────────────────────────────────

@Composable
fun SceneRenderer(scene: Scene, isPreview: Boolean = false, replayKey: Int = 0) {
    when (scene.type) {
        SceneType.TEXT -> TextSceneRenderer(scene, isPreview, replayKey)
        SceneType.IMAGE -> ImageSceneRenderer(scene)
        SceneType.VIDEO -> VideoSceneRenderer(scene)
    }
}

@Composable
fun TextSceneRenderer(scene: Scene, isPreview: Boolean = false, replayKey: Int = 0) {
    val content = scene.content
    val text = content.source ?: ""
    val colorConfig = content.color
    val motion = content.motion?.scroll
    val animation = content.animation?.blink


    val mirror = content.transform?.mirror ?: false

    // Compute color based on config
    val baseColor = when (colorConfig?.type) {
        ColorType.STATIC -> parseColor(colorConfig.value)
        ColorType.TOGGLE -> {
            val intervalMs = frequencyToMs(colorConfig.frequency ?: 5)
            if (intervalMs <= 0L) {
                parseColor(colorConfig.from)
            } else {
                var showFrom by remember(replayKey) { mutableStateOf(true) }
                LaunchedEffect(colorConfig.frequency ?: 5, replayKey) {
                    while (true) {
                        delay(intervalMs)
                        showFrom = !showFrom
                    }
                }
                if (showFrom) parseColor(colorConfig.from)
                else parseColor(colorConfig.to ?: "#FFFF00")
            }
        }
        ColorType.GRADIENT -> {
            val durMs = (colorConfig.duration ?: 5000).coerceAtLeast(100)
            var gradient by remember(replayKey) { mutableStateOf(0f) }
            LaunchedEffect(durMs, replayKey) {
                val step = 16L
                val totalSteps = (durMs / step).coerceAtLeast(1).toInt()
                var stepCount = 0
                while (true) {
                    kotlinx.coroutines.delay(step)
                    stepCount++
                    gradient = ((stepCount % totalSteps).toFloat() / totalSteps)
                }
            }
            val from = parseColor(colorConfig.from)
            val to = parseColor(colorConfig.to ?: "#FFFF00")
            Color(
                red = from.red + (to.red - from.red) * gradient,
                green = from.green + (to.green - from.green) * gradient,
                blue = from.blue + (to.blue - from.blue) * gradient,
                alpha = from.alpha + (to.alpha - from.alpha) * gradient
            )
        }
        else -> Color.White
    }

    // Blink animation
    val blinkAlpha = if (animation != null) {
        val intervalMs = frequencyToMs(animation.frequency)
        if (intervalMs <= 0L) {
            1f
        } else {
            var showFrom by remember(replayKey) { mutableStateOf(true) }
            LaunchedEffect(animation.frequency, replayKey) {
                while (true) {
                    delay(intervalMs)
                    showFrom = !showFrom
                }
            }
            if (showFrom) 1f else 0f
        }
    } else 1f

    // Scroll animation — timer-based fraction 0..1
    val scrollFraction = if (motion != null) {
        var fraction by remember(replayKey) { mutableStateOf(0f) }
        val speed = motion.speed.coerceIn(1, 10)
        val durationMs = (14000f / speed).toInt()
        val isContinuous = motion.continuous
        LaunchedEffect(durationMs, isPreview, replayKey) {
            val step = if (isPreview) 33L else 16L
            val totalSteps = (durationMs / step.toInt()).coerceAtLeast(1)
            var stepCount = 0
            while (true) {
                kotlinx.coroutines.delay(step)
                stepCount++
                fraction = if (isContinuous)
                    stepCount.toFloat() / totalSteps  // never wraps — copies fill screen
                else
                    (stepCount % totalSteps).toFloat() / totalSteps
            }
        }
        fraction
    } else 0f

    val direction = motion?.direction

    // Background color (supports STATIC, TOGGLE, GRADIENT)
    val bgColorConfig = scene.appearance.backgroundColor
    val bgColor = when (bgColorConfig?.type) {
        ColorType.STATIC -> parseColor(bgColorConfig.value)
        ColorType.TOGGLE -> {
            val intervalMs = frequencyToMs(bgColorConfig.frequency ?: 5)
            if (intervalMs <= 0L) {
                parseColor(bgColorConfig.from)
            } else {
                var showFrom by remember { mutableStateOf(true) }
                LaunchedEffect(bgColorConfig.frequency ?: 5) {
                    while (true) {
                        delay(intervalMs)
                        showFrom = !showFrom
                    }
                }
                if (showFrom) parseColor(bgColorConfig.from)
                else parseColor(bgColorConfig.to ?: "#FFFF00")
            }
        }
        ColorType.GRADIENT -> {
            val durMs = (bgColorConfig.duration ?: 3000).coerceAtLeast(100)
            var gradient by remember { mutableStateOf(0f) }
            LaunchedEffect(durMs) {
                val step = 16L
                val totalSteps = (durMs / step).coerceAtLeast(1).toInt()
                var stepCount = 0
                while (true) {
                    kotlinx.coroutines.delay(step)
                    stepCount++
                    gradient = ((stepCount % totalSteps).toFloat() / totalSteps)
                }
            }
            val from = parseColor(bgColorConfig.from)
            val to = parseColor(bgColorConfig.to ?: "#FFFF00")
            Color(
                red = from.red + (to.red - from.red) * gradient,
                green = from.green + (to.green - from.green) * gradient,
                blue = from.blue + (to.blue - from.blue) * gradient,
                alpha = from.alpha + (to.alpha - from.alpha) * gradient
            )
        }
        else -> Color.Black
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val isHorizontalScroll = direction == ScrollDirection.LEFT_TO_RIGHT ||
                    direction == ScrollDirection.RIGHT_TO_LEFT
            val paint = android.graphics.Paint().apply {
                color = baseColor.toArgb()
                textSize = relativeTextSizePx(
                    configuredSize = content.style?.size ?: 40,
                    viewportShortSide = minOf(size.width, size.height)
                )
                textAlign = if (isHorizontalScroll) android.graphics.Paint.Align.LEFT
                            else android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                alpha = (blinkAlpha * 255).toInt()
                typeface = typefaceForKey(content.style?.fontFamily)
            }

            val textWidth = paint.measureText(text)
            val fontHeight = paint.descent() - paint.ascent()
            val x = when (direction) {
                ScrollDirection.LEFT_TO_RIGHT ->
                    -textWidth + (size.width + textWidth) * scrollFraction
                ScrollDirection.RIGHT_TO_LEFT ->
                    size.width - (size.width + textWidth) * scrollFraction
                else -> center.x
            }
            val y = when (direction) {
                ScrollDirection.TOP_TO_BOTTOM ->
                    -paint.descent() + (size.height + fontHeight) * scrollFraction
                ScrollDirection.BOTTOM_TO_TOP ->
                    size.height - paint.ascent() - (size.height + fontHeight) * scrollFraction
                else -> center.y - (paint.descent() + paint.ascent()) / 2
            }

            val continuous = motion?.continuous == true

            fun drawAt(drawX: Float, drawY: Float) {
                if (mirror) {
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.scale(-1f, 1f, center.x, center.y)
                    drawContext.canvas.nativeCanvas.drawText(text, drawX, drawY, paint)
                    drawContext.canvas.nativeCanvas.restore()
                } else {
                    drawContext.canvas.nativeCanvas.drawText(text, drawX, drawY, paint)
                }
            }

            drawAt(x, y)
            if (continuous) {
                if (isHorizontalScroll) {
                    // Draw enough copies to fill the viewport completely
                    val copiesNeeded = (size.width / textWidth).toInt() + 2
                    for (i in 1..copiesNeeded) {
                        drawAt(x - i * textWidth, y)
                        drawAt(x + i * textWidth, y)
                    }
                } else {
                    val copiesNeeded = (size.height / fontHeight).toInt() + 2
                    for (i in 1..copiesNeeded) {
                        drawAt(x, y - i * fontHeight)
                        drawAt(x, y + i * fontHeight)
                    }
                }
            }
        }

        // Overlay dots
        scene.appearance.overlay?.forEach { overlay ->
            if (overlay.type == OverlayType.DOT_MASK) {
                DotMaskOverlay(overlay)
            }
        }
    }
}

@Composable
fun ImageSceneRenderer(scene: Scene) {
    val source = scene.content.source
    val mirror = scene.content.transform?.mirror ?: false
    val blinkAnim = scene.content.animation?.blink

    val blinkAlpha = if (blinkAnim != null) {
        val intervalMs = frequencyToMs(blinkAnim.frequency)
        if (intervalMs <= 0L) {
            1f
        } else {
            var showFrom by remember { mutableStateOf(true) }
            LaunchedEffect(blinkAnim.frequency) {
                while (true) {
                    delay(intervalMs)
                    showFrom = !showFrom
                }
            }
            if (showFrom) 1f else 0f
        }
    } else 1f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(parseColor(scene.appearance.backgroundColor?.value))
            .graphicsLayer(
                alpha = blinkAlpha,
                scaleX = if (mirror) -1f else 1f
            )
    ) {
        if (!source.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(source)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(com.biexi.pandaled.R.string.scene_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(com.biexi.pandaled.R.string.editor_missing_image), color = Color.Red, fontSize = 14.sp)
            }
        }

        // Overlays
        scene.appearance.overlay?.forEach { overlay ->
            if (overlay.type == OverlayType.DOT_MASK) {
                DotMaskOverlay(overlay)
            }
        }
    }
}

@Composable
fun VideoSceneRenderer(scene: Scene) {
    val source = scene.content.source
    val mirror = scene.content.transform?.mirror ?: false
    val blinkAnim = scene.content.animation?.blink
    val context = LocalContext.current

    val blinkAlpha = if (blinkAnim != null) {
        val intervalMs = frequencyToMs(blinkAnim.frequency)
        if (intervalMs <= 0L) {
            1f
        } else {
            var showFrom by remember { mutableStateOf(true) }
            LaunchedEffect(blinkAnim.frequency) {
                while (true) {
                    delay(intervalMs)
                    showFrom = !showFrom
                }
            }
            if (showFrom) 1f else 0f
        }
    } else 1f

    // Background color (support STATIC, TOGGLE, GRADIENT)
    val bgCfg = scene.appearance.backgroundColor
    val bgColor = when (bgCfg?.type) {
        ColorType.STATIC -> parseColor(bgCfg.value)
        ColorType.TOGGLE -> {
            val intervalMs = frequencyToMs(bgCfg.frequency ?: 5)
            if (intervalMs <= 0L) {
                parseColor(bgCfg.from)
            } else {
                var showFrom by remember { mutableStateOf(true) }
                LaunchedEffect(bgCfg.frequency ?: 5) {
                    while (true) {
                        delay(intervalMs)
                        showFrom = !showFrom
                    }
                }
                if (showFrom) parseColor(bgCfg.from) else parseColor(bgCfg.to ?: "#000000")
            }
        }
        ColorType.GRADIENT -> {
            val durMs = (bgCfg.duration ?: 3000).coerceAtLeast(100)
            var gradient by remember { mutableStateOf(0f) }
            LaunchedEffect(durMs) {
                val step = 16L
                val totalSteps = (durMs / step).coerceAtLeast(1).toInt()
                var stepCount = 0
                while (true) {
                    kotlinx.coroutines.delay(step)
                    stepCount++
                    gradient = ((stepCount % totalSteps).toFloat() / totalSteps)
                }
            }
            val from = parseColor(bgCfg.from)
            val to = parseColor(bgCfg.to ?: "#FF0000")
            Color(
                red = from.red + (to.red - from.red) * gradient,
                green = from.green + (to.green - from.green) * gradient,
                blue = from.blue + (to.blue - from.blue) * gradient,
                alpha = from.alpha + (to.alpha - from.alpha) * gradient
            )
        }
        else -> Color.Black
    }

    // ExoPlayer — mute video
    val player = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            volume = 0f
        }
    }
    var isReady by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        isReady = false
        player.stop()
        if (!source.isNullOrBlank()) {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(source)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            player.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        }
    }

    // Track player readiness
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    isReady = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.stop()
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (!isReady) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        if (!source.isNullOrBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = blinkAlpha),
                factory = {
                    (android.view.LayoutInflater.from(context)
                        .inflate(com.biexi.pandaled.R.layout.view_video_player_texture, null) as PlayerView).apply {
                        this.player = player
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    view.scaleX = if (mirror) -1f else 1f
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(com.biexi.pandaled.R.string.fullscreen_video_missing), color = Color.Red, fontSize = 14.sp)
            }
        }

        // Overlays
        scene.appearance.overlay?.forEach { overlay ->
            if (overlay.type == OverlayType.DOT_MASK) {
                DotMaskOverlay(overlay)
            }
        }
    }
}

// ─── Dot Mask Overlay ────────────────────────────────────

@Composable
fun DotMaskOverlay(overlay: Overlay) {
    val style = overlay.style
    val overlaySize = overlay.size.coerceIn(1, 10)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val viewShort = size.width.coerceAtMost(size.height)
                val requestedDotSize = viewShort * (overlaySize.toFloat() / 80f)
                val spacing = maxOf(
                    requestedDotSize * 1.3f,
                    size.width / 180f,
                    size.height / 100f,
                    1f
                )
                val dotSize = minOf(requestedDotSize, spacing * 0.75f)
                val holePath = android.graphics.Path()
                val rect = android.graphics.RectF()

                var x = 0f
                var y = 0f
                while (y < size.height) {
                    x = 0f
                    while (x < size.width) {
                        when (style) {
                            "round" -> {
                                holePath.addCircle(
                                    x + dotSize / 2,
                                    y + dotSize / 2,
                                    dotSize / 2,
                                    android.graphics.Path.Direction.CW
                                )
                            }
                            "square" -> {
                                rect.set(x, y, x + dotSize, y + dotSize)
                                holePath.addRect(rect, android.graphics.Path.Direction.CW)
                            }
                            "heart" -> {
                                val cx = x + dotSize / 2
                                val cy = y + dotSize / 2
                                val s = dotSize * 0.48f
                                // Fatter heart: wider lobes, rounder bottom
                                holePath.moveTo(cx, cy + s * 1.05f)
                                holePath.cubicTo(
                                    cx - s * 1.2f, cy - s * 0.1f,
                                    cx - s * 1.0f, cy - s * 1.3f,
                                    cx, cy - s * 0.55f
                                )
                                holePath.cubicTo(
                                    cx + s * 1.0f, cy - s * 1.3f,
                                    cx + s * 1.2f, cy - s * 0.1f,
                                    cx, cy + s * 1.05f
                                )
                            }
                        }
                        x += spacing
                    }
                    y += spacing
                }

                val maskPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    isAntiAlias = true
                }
                val clearPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
                }

                onDrawBehind {
                    val nativeCanvas = drawContext.canvas.nativeCanvas
                    nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                    nativeCanvas.drawRect(0f, 0f, size.width, size.height, maskPaint)
                    nativeCanvas.drawPath(holePath, clearPaint)
                    nativeCanvas.restore()
                }
            }
    )
}

// ─── Transition Overlay ──────────────────────────────────

@Composable
fun TransitionOverlay(
    type: TransitionType,
    duration: Long,
    bgColor: String = "#000000",
    replayKey: Int = 0
) {
    val durationMs = if (duration > 0) duration.toInt().coerceAtLeast(100) else 1000
    val curtainColor = parseColor(bgColor)

    val offsetFraction = remember { Animatable(1f) }

    LaunchedEffect(type, durationMs, replayKey) {
        offsetFraction.snapTo(1f)
        offsetFraction.animateTo(
            0f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
        )
    }

    val fraction = offsetFraction.value

    Canvas(modifier = Modifier.fillMaxSize()) {
        when (type) {
            TransitionType.FADE_IN -> {
                // Curtain fades from opaque to transparent
                drawRect(
                    color = curtainColor.copy(alpha = fraction),
                    topLeft = Offset.Zero,
                    size = size
                )
            }
            TransitionType.SLIDE_IN_FROM_LEFT -> {
                // Content slides in from left
                drawRect(
                    color = curtainColor,
                    topLeft = Offset(size.width * (1f - fraction), 0f),
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                )
            }
            TransitionType.SLIDE_IN_FROM_RIGHT -> {
                // Content slides in from right
                drawRect(
                    color = curtainColor,
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                )
            }
            TransitionType.SLIDE_IN_FROM_TOP -> {
                // Content slides in from top
                drawRect(
                    color = curtainColor,
                    topLeft = Offset(0f, size.height * (1f - fraction)),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * fraction)
                )
            }
            TransitionType.SLIDE_IN_FROM_BOTTOM -> {
                // Content slides in from bottom
                drawRect(
                    color = curtainColor,
                    topLeft = Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(size.width, size.height * fraction)
                )
            }
            TransitionType.NONE -> { /* no-op */ }
        }
    }
}

// ─── Utility ─────────────────────────────────────────────

private fun parseColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color.Black
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color.Black
    }
}

private fun relativeTextSizePx(configuredSize: Int, viewportShortSide: Float): Float {
    val normalizedSize = configuredSize.coerceIn(1, 200)
    return viewportShortSide * (normalizedSize / 144f)
}
