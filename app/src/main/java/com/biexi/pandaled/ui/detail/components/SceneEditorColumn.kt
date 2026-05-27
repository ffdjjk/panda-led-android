package com.biexi.pandaled.ui.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biexi.pandaled.data.model.*
import com.biexi.pandaled.ui.detail.EditorTarget
import androidx.compose.ui.res.stringResource
import com.biexi.pandaled.R

/**
 * Right column: detailed editor for the currently selected idleScene or scene.
 * When the user edits a field, the preview re-plays that scene.
 */
@Composable
fun SceneEditorColumn(
    project: Project,
    selectedTarget: EditorTarget,
    highlightedSceneIndex: Int?,
    onUpdateIdleScene: (IdleScene) -> Unit,
    onUpdateScene: (Int, Scene) -> Unit,
    onNavigateToInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (selectedTarget) {
            is EditorTarget.None -> {
                EditorSection(title = stringResource(R.string.detail_tab_editor)) {
                    Text(
                        stringResource(R.string.editor_select_scene),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            is EditorTarget.IdleScene -> {
                IdleSceneEditor(
                    idleScene = project.idleScene,
                    onUpdate = onUpdateIdleScene,
                    onNavigateToInfo = onNavigateToInfo
                )
            }
            is EditorTarget.Scene -> {
                val scene = project.scenes.getOrNull(selectedTarget.index)
                if (scene != null) {
                    SceneEditor(
                        scene = scene,
                        sceneIndex = selectedTarget.index,
                        isHighlighted = highlightedSceneIndex == selectedTarget.index,
                        onUpdate = { updated -> onUpdateScene(selectedTarget.index, updated) }
                    )
                } else {
                    Text(stringResource(R.string.editor_scene_not_found), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ─── Idle Scene Editor ───────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdleSceneEditor(
    idleScene: IdleScene,
    onUpdate: (IdleScene) -> Unit,
    onNavigateToInfo: () -> Unit = {}
) {
    var type by remember(idleScene) { mutableStateOf(idleScene.type) }

    EditorSection(stringResource(R.string.scene_waiting)) {
        // Hint box: "This scene is shown before the project starts."
        Card(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            FlowRow(modifier = Modifier.padding(10.dp)) {
                Text(
                    stringResource(R.string.idle_waiting_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.idle_view_start_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateToInfo() }
                )
            }
        }
        FieldLabel(stringResource(R.string.editor_show_mode))
        TypeSelector(
            options = IdleSceneType.entries.map { it.name to idleSceneTypeLabel(it) },
            selected = type.name,
            onSelect = { selectedName ->
                val newType = IdleSceneType.valueOf(selectedName)
                type = newType
                onUpdate(idleScene.copy(type = newType, content = IdleSceneContent()))
            }
        )

        when (type) {
            IdleSceneType.NONE -> {
                Text(
                    stringResource(R.string.idle_black_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IdleSceneType.CLOCK -> {
                IdleClockCountdownEditor(
                    content = idleScene.content,
                    isClock = true,
                    onUpdate = { c -> onUpdate(idleScene.copy(content = c)) }
                )
            }
            IdleSceneType.COUNTDOWN -> {
                IdleClockCountdownEditor(
                    content = idleScene.content,
                    isClock = false,
                    onUpdate = { c -> onUpdate(idleScene.copy(content = c)) }
                )
            }
            IdleSceneType.IMAGE -> {
                IdleImageEditor(
                    content = idleScene.content,
                    onUpdate = { c -> onUpdate(idleScene.copy(content = c)) }
                )
            }
        }
    }
}

@Composable
fun IdleClockCountdownEditor(
    content: IdleSceneContent?,
    isClock: Boolean,
    onUpdate: (IdleSceneContent) -> Unit
) {
    val current = content ?: IdleSceneContent()
    // Force white for both clock and countdown — override any legacy orange
    val defaultColor = "#FFFFFF"
    val contentKey = System.identityHashCode(content)
    var format by remember(contentKey) { mutableStateOf(current.style?.format ?: "{hours}:{minutes}:{seconds}") }
    var fontFamily by remember(contentKey) { mutableStateOf(current.style?.fontFamily ?: "sans-serif") }
    var size by remember(contentKey) { mutableStateOf((current.style?.size ?: 40).toString()) }
    var color by remember(contentKey) { mutableStateOf(current.style?.color?.takeIf { it != "#FFFFFF" } ?: defaultColor) }

    fun emit() {
        onUpdate(
            current.copy(
                style = ContentStyle(
                    format = format,
                    fontFamily = fontFamily,
                    size = size.toIntOrNull() ?: 40,
                    color = color
                )
            )
        )
    }

    OutlinedTextField(
        value = format,
        onValueChange = { format = it; emit() },
        label = { Text(stringResource(R.string.editor_format)) },
        placeholder = { Text("{hours}:{minutes}:{seconds}") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    FieldLabel(stringResource(R.string.editor_font))
    FontFamilySelector(
        selectedKey = fontFamily,
        onSelect = { fontFamily = it; emit() },
        modifier = Modifier.fillMaxWidth()
    )
    NumberStepper(
        label = stringResource(R.string.editor_font_size),
        value = size,
        onValueChange = { size = it; emit() }
    )
    ColorField(
        label = stringResource(R.string.editor_color),
        selectedHex = color,
        onSelect = { color = it; emit() }
    )
}

@Composable
fun IdleImageEditor(
    content: IdleSceneContent?,
    onUpdate: (IdleSceneContent) -> Unit
) {
    val current = content ?: IdleSceneContent()
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = copyToAppStorage(context, uri, "img")
            onUpdate(current.copy(source = path))
        }
    }
    val source = current.source

    Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
        Text(if (source.isNullOrBlank()) stringResource(R.string.editor_select_image) else stringResource(R.string.editor_replace_image))
    }
    if (source.isNullOrBlank()) {
        Text(stringResource(R.string.editor_missing_image), color = Color.Red, style = MaterialTheme.typography.bodySmall)
    }
}

// ─── Scene Editor ────────────────────────────────────────

@Composable
fun SceneEditor(
    scene: Scene,
    sceneIndex: Int,
    isHighlighted: Boolean,
    onUpdate: (Scene) -> Unit
) {
    var name by remember(scene) { mutableStateOf(scene.name) }
    var type by remember(scene) { mutableStateOf(scene.type) }
    var durationSec by remember(scene) { mutableIntStateOf(scene.duration.coerceIn(1, 300)) }
    var playMode by remember(scene) { mutableStateOf(scene.playMode) }

    EditorSection(stringResource(R.string.editor_basic)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onUpdate(scene.copy(name = it))
            },
            label = { Text(stringResource(R.string.editor_scene_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        NumberStepper(
            label = stringResource(R.string.editor_duration),
            value = durationSec.toString(),
            min = 1,
            max = 300,
            onValueChange = {
                val newDuration = it.toIntOrNull() ?: 5
                durationSec = newDuration
                onUpdate(scene.copy(duration = newDuration))
            }
        )

        val isLoop = playMode == PlayMode.LOOP
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FieldLabel(stringResource(R.string.editor_play_mode))
            Spacer(Modifier.weight(1f))
            Text(
                if (isLoop) stringResource(R.string.editor_loop) else stringResource(R.string.editor_once),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = isLoop,
                onCheckedChange = {
                    playMode = if (it) PlayMode.LOOP else PlayMode.ONCE
                    onUpdate(scene.copy(playMode = playMode))
                }
            )
        }
    }

    val content = scene.content

    EditorSection(stringResource(R.string.editor_content)) {
        when (type) {
            SceneType.TEXT -> TextContentEditor(content) { onUpdate(scene.copy(content = it)) }
            SceneType.IMAGE -> ImageContentEditor(content) { onUpdate(scene.copy(content = it)) }
            SceneType.VIDEO -> VideoContentEditor(content) { onUpdate(scene.copy(content = it)) }
        }
    }

    // ─── Transition section (hide for TEXT) ─────────────
    if (type != SceneType.TEXT) {
        EditorSection(stringResource(R.string.editor_transition)) {
            val transition = scene.transition
            val enter = transition.enter

            FieldLabel(stringResource(R.string.editor_enter_anim))
            TypeSelector(
                options = TransitionType.entries.map { it.name to transitionTypeLabel(it) },
                selected = enter.type.name,
                onSelect = { selected ->
                    val tt = TransitionType.valueOf(selected)
                    val wasNone = enter.type == TransitionType.NONE
                    onUpdate(
                        scene.copy(
                            transition = transition.copy(
                                enter = enter.copy(
                                    type = tt,
                                    duration = if (wasNone && tt != TransitionType.NONE) 2f else enter.duration
                                )
                            )
                        )
                    )
                }
            )

            if (enter.type != TransitionType.NONE) {
                var enterSeconds by remember(enter) { mutableIntStateOf(enter.duration.toInt().coerceIn(0, 30)) }
                NumberStepper(
                    label = stringResource(R.string.editor_anim_duration),
                    value = enterSeconds.toString(),
                    min = 0,
                    max = 30,
                    onValueChange = {
                        enterSeconds = it.toIntOrNull() ?: 1
                        onUpdate(
                            scene.copy(
                                transition = transition.copy(
                                    enter = enter.copy(duration = enterSeconds.toFloat())
                                )
                            )
                        )
                    }
                )
            }
        }
    }

    EditorSection(stringResource(R.string.editor_appearance)) {
        AppearanceEditor(
            appearance = scene.appearance,
            sceneType = type,
            onUpdate = { onUpdate(scene.copy(appearance = it)) }
        )
    }
}

// ─── Type Selector (dropdown-style for multi-mode keys) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeSelector(
    options: List<Pair<String, String>>,  // (key, label)
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ─── Text Content Editor ─────────────────────────────────

@Composable
fun TextContentEditor(content: SceneContent, onUpdate: (SceneContent) -> Unit) {
    var source by remember(content) { mutableStateOf(content.source ?: "") }

    OutlinedTextField(
        value = source,
        onValueChange = {
            source = it
            onUpdate(content.copy(source = it))
        },
        label = { Text(stringResource(R.string.editor_text_content)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false
        )
    )

    // Style
    var fontSize by remember(content) { mutableStateOf((content.style?.size ?: 40).toString()) }
    var fontFamily by remember(content) { mutableStateOf(content.style?.fontFamily ?: "sans-serif") }

    NumberStepper(
        label = stringResource(R.string.editor_font_size),
        value = fontSize,
        min = 6,
        max = 200,
        onValueChange = {
            fontSize = it
            onUpdate(content.copy(style = (content.style ?: ContentStyle()).copy(size = it.toIntOrNull() ?: 40)))
        }
    )
    FieldLabel(stringResource(R.string.editor_font))
    FontFamilySelector(
        selectedKey = fontFamily,
        onSelect = {
            fontFamily = it
            onUpdate(content.copy(style = (content.style ?: ContentStyle()).copy(fontFamily = it)))
        },
        modifier = Modifier.fillMaxWidth()
    )

    // Color config
    ColorConfigEditor(
        colorConfig = content.color,
        onUpdate = { onUpdate(content.copy(color = it)) }
    )

    // Motion — scroll
    var hasScroll by remember(content) { mutableStateOf(content.motion?.scroll != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(stringResource(R.string.editor_scroll))
        Spacer(Modifier.weight(1f))
        Switch(
            checked = hasScroll,
            onCheckedChange = {
                hasScroll = it
                onUpdate(
                    content.copy(
                        motion = if (it) Motion(scroll = ScrollConfig()) else null
                    )
                )
            }
        )
    }
    if (hasScroll) {
        val scroll = content.motion?.scroll ?: ScrollConfig()
        var direction by remember(scroll) { mutableStateOf(scroll.direction) }
        var speed by remember(scroll) { mutableIntStateOf(scroll.speed.coerceIn(1, 10)) }

        TypeSelector(
            options = ScrollDirection.entries.map { it.name to scrollDirectionLabel(it) },
            selected = direction.name,
            onSelect = {
                direction = ScrollDirection.valueOf(it)
                onUpdate(content.copy(motion = Motion(scroll = scroll.copy(direction = direction))))
            }
        )
        LabeledSlider(
            label = stringResource(R.string.editor_scroll_speed),
            sliderKey = "scrollSpeed",
            value = speed,
            range = 1..10,
            onValueChange = {
                speed = it
                onUpdate(content.copy(motion = Motion(scroll = scroll.copy(speed = it))))
            }
        )
        // Continuous toggle
        var isContinuous by remember(scroll) { mutableStateOf(scroll.continuous) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(stringResource(R.string.editor_continuous))
            Spacer(Modifier.weight(1f))
            Switch(
                checked = isContinuous,
                onCheckedChange = {
                    isContinuous = it
                    onUpdate(content.copy(motion = Motion(scroll = scroll.copy(continuous = it))))
                }
            )
        }
    }

    // Transform — mirror
    var mirror by remember(content) { mutableStateOf(content.transform?.mirror ?: false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(stringResource(R.string.editor_mirror))
        Spacer(Modifier.weight(1f))
        Switch(
            checked = mirror,
            onCheckedChange = {
                mirror = it
                onUpdate(content.copy(transform = Transform(mirror = it)))
            }
        )
    }

    // Blink — slider 0-10, 0=off
    val blink = content.animation?.blink ?: BlinkConfig(frequency = 0)
    var blinkFreq by remember(blink) { mutableStateOf(blink.frequency.coerceIn(0, 10)) }
    LabeledSlider(
        label = stringResource(R.string.editor_blink),
        sliderKey = "textBlinkFreq",
        value = blinkFreq,
        range = 0..10,
        onValueChange = {
            blinkFreq = it
            onUpdate(
                content.copy(
                    animation = if (it == 0) null
                                else Animation(blink = blink.copy(frequency = it))
                )
            )
        }
    )

}

// ─── Image Content Editor ────────────────────────────────

@Composable
fun ImageContentEditor(content: SceneContent, onUpdate: (SceneContent) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = copyToAppStorage(context, uri, "img")
            onUpdate(content.copy(source = path))
        }
    }
    val source = content.source

    Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
        Text(if (source.isNullOrBlank()) stringResource(R.string.editor_select_image) else stringResource(R.string.editor_replace_image))
    }
    if (source.isNullOrBlank()) {
        Text(stringResource(R.string.editor_missing_image), color = Color.Red, style = MaterialTheme.typography.bodySmall)
    }

    // Blink — slider 0-10, 0=off
    val blink = content.animation?.blink ?: BlinkConfig(frequency = 0)
    var blinkFreq by remember(blink) { mutableStateOf(blink.frequency.coerceIn(0, 10)) }
    LabeledSlider(
        label = stringResource(R.string.editor_blink),
        sliderKey = "imgBlinkFreq",
        value = blinkFreq,
        range = 0..10,
        onValueChange = {
            blinkFreq = it
            onUpdate(
                content.copy(
                    animation = if (it == 0) null
                                else Animation(blink = blink.copy(frequency = it))
                )
            )
        }
    )
}

// ─── Video Content Editor ────────────────────────────────

@Composable
fun VideoContentEditor(content: SceneContent, onUpdate: (SceneContent) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = copyToAppStorage(context, uri, "video")
            onUpdate(content.copy(source = path))
        }
    }
    val source = content.source

    Button(onClick = { picker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
        Text(if (source.isNullOrBlank()) stringResource(R.string.editor_select_video) else stringResource(R.string.editor_replace_video))
    }
    if (source.isNullOrBlank()) {
        Text(stringResource(R.string.editor_missing_video), color = Color.Red, style = MaterialTheme.typography.bodySmall)
    } else {
        // Show video duration for reference
        var videoDurationSec by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(source) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(source)
                val durMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                videoDurationSec = (durMs / 1000).toInt()
            } catch (_: Exception) {
                videoDurationSec = null
            } finally {
                retriever.release()
            }
        }
        Text(
            "${stringResource(R.string.editor_video_duration)}: ${videoDurationSec?.let { "${it}s" } ?: stringResource(R.string.editor_video_unknown)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    // Blink — slider 0-10, 0=off
    val blink = content.animation?.blink ?: BlinkConfig(frequency = 0)
    var blinkFreq by remember(blink) { mutableStateOf(blink.frequency.coerceIn(0, 10)) }
    LabeledSlider(
        label = stringResource(R.string.editor_blink),
        sliderKey = "videoBlinkFreq",
        value = blinkFreq,
        range = 0..10,
        onValueChange = {
            blinkFreq = it
            onUpdate(
                content.copy(
                    animation = if (it == 0) null
                                else Animation(blink = blink.copy(frequency = it))
                )
            )
        }
    )
}

// ─── Color Config Editor ─────────────────────────────────

@Composable
fun ColorConfigEditor(
    colorConfig: ColorConfig?,
    onUpdate: (ColorConfig) -> Unit
) {
    val current = colorConfig ?: ColorConfig()
    var mode by remember(current) { mutableStateOf(current.type) }

    FieldLabel(stringResource(R.string.editor_color_mode))
    TypeSelector(
        options = listOf(
            "STATIC" to stringResource(R.string.editor_color_static),
            "TOGGLE" to stringResource(R.string.editor_color_toggle),
            "GRADIENT" to stringResource(R.string.editor_color_gradient)
        ),
        selected = mode.name,
        onSelect = { selected ->
            mode = ColorType.valueOf(selected)
            onUpdate(current.copy(type = mode))
        }
    )

    when (mode) {
        ColorType.STATIC -> {
            var value by remember(current) { mutableStateOf(current.value ?: "#FFFFFF") }
            ColorField(
                label = stringResource(R.string.editor_color),
                selectedHex = value,
                onSelect = {
                    value = it
                    onUpdate(current.copy(type = ColorType.STATIC, value = it))
                }
            )
        }
        ColorType.TOGGLE -> {
            var from by remember(current) { mutableStateOf(current.from ?: "#FFFFFF") }
            var to by remember(current) { mutableStateOf(current.to ?: "#FF0000") }
            var freq by remember(current) { mutableStateOf(current.frequency?.toString() ?: "5") }

            ColorField(
                label = stringResource(R.string.color_a),
                selectedHex = from,
                onSelect = {
                    from = it
                    onUpdate(current.copy(type = ColorType.TOGGLE, from = it, to = to, frequency = freq.toIntOrNull() ?: 5))
                }
            )
            Spacer(Modifier.height(8.dp))
            ColorField(
                label = stringResource(R.string.color_b),
                selectedHex = to,
                onSelect = {
                    to = it
                    onUpdate(current.copy(type = ColorType.TOGGLE, from = from, to = it, frequency = freq.toIntOrNull() ?: 5))
                }
            )
            LabeledSlider(
                label = stringResource(R.string.editor_frequency),
                sliderKey = "colorToggleFreq",
                value = freq.toIntOrNull() ?: 5,
                onValueChange = {
                    freq = it.toString()
                    onUpdate(current.copy(type = ColorType.TOGGLE, from = from, to = to, frequency = it))
                }
            )
        }
        ColorType.GRADIENT -> {
            var from by remember(current) { mutableStateOf(current.from ?: "#FFFFFF") }
            var to by remember(current) { mutableStateOf(current.to ?: "#FF0000") }
            val storedMs = current.duration ?: 1160L
            var durSlider by remember(current) { mutableStateOf(
                when {
                    storedMs >= 1498L -> 1
                    storedMs >= 1333L -> 2
                    storedMs >= 1168L -> 3
                    storedMs >= 1003L -> 4
                    storedMs >= 841L -> 5
                    storedMs >= 678L -> 6
                    storedMs >= 513L -> 7
                    storedMs >= 348L -> 8
                    storedMs >= 183L -> 9
                    storedMs > 0L -> 10
                    else -> 0
                }
            ) }

            ColorField(
                label = stringResource(R.string.editor_start_color),
                selectedHex = from,
                onSelect = {
                    from = it
                    onUpdate(current.copy(type = ColorType.GRADIENT, from = it, to = to))
                }
            )
            Spacer(Modifier.height(8.dp))
            ColorField(
                label = stringResource(R.string.editor_end_color),
                selectedHex = to,
                onSelect = {
                    to = it
                    onUpdate(current.copy(type = ColorType.GRADIENT, from = from, to = it))
                }
            )

            LabeledSlider(
                label = "频率",
                sliderKey = "colorGradientFreq",
                value = durSlider,
                onValueChange = {
                    durSlider = it
                    val ms = frequencyToMs(it)
                    onUpdate(current.copy(type = ColorType.GRADIENT, from = from, to = to, duration = ms))
                }
            )
        }
    }
}

// ─── Appearance Editor ───────────────────────────────────

@Composable
fun BackgroundColorEditor(
    bg: ColorConfig,
    onUpdate: (ColorConfig) -> Unit,
    modes: List<ColorType> = ColorType.entries.toList()
) {
    val singleMode = modes.size == 1
    var bgMode by remember(bg) { mutableStateOf(if (bg.type in modes) bg.type else modes.first()) }

    if (!singleMode) {
        FieldLabel(stringResource(R.string.editor_bg_color_mode))
        TypeSelector(
            options = modes.map { it.name to when (it) {
                ColorType.STATIC -> stringResource(R.string.editor_color_static)
                ColorType.TOGGLE -> stringResource(R.string.editor_color_toggle)
                ColorType.GRADIENT -> stringResource(R.string.editor_color_gradient)
            }},
            selected = bgMode.name,
            onSelect = {
                bgMode = ColorType.valueOf(it)
                onUpdate(bg.copy(type = bgMode))
            }
        )
    }

    when (bgMode) {
        ColorType.STATIC -> {
            var bgValue by remember(bg) { mutableStateOf(bg.value ?: "#000000") }
            ColorField(
                label = stringResource(R.string.editor_bg_color),
                selectedHex = bgValue,
                onSelect = {
                    bgValue = it
                    onUpdate(ColorConfig(type = ColorType.STATIC, value = it))
                }
            )
        }
        ColorType.TOGGLE -> {
            var from by remember(bg) { mutableStateOf(bg.from ?: "#000000") }
            var to by remember(bg) { mutableStateOf(bg.to ?: "#FFFFFF") }
            var freq by remember(bg) { mutableStateOf(bg.frequency ?: 5) }

            ColorField(
                label = stringResource(R.string.editor_bg_color_a),
                selectedHex = from,
                onSelect = {
                    from = it
                    onUpdate(bg.copy(type = ColorType.TOGGLE, from = it, to = to, frequency = freq))
                }
            )
            Spacer(Modifier.height(8.dp))
            ColorField(
                label = stringResource(R.string.editor_bg_color_b),
                selectedHex = to,
                onSelect = {
                    to = it
                    onUpdate(bg.copy(type = ColorType.TOGGLE, from = from, to = it, frequency = freq))
                }
            )
            LabeledSlider(
                label = stringResource(R.string.editor_frequency),
                sliderKey = "bgToggleFreq",
                value = freq,
                onValueChange = {
                    freq = it
                    onUpdate(bg.copy(type = ColorType.TOGGLE, from = from, to = to, frequency = it))
                }
            )
        }
        ColorType.GRADIENT -> {
            var from by remember(bg) { mutableStateOf(bg.from ?: "#000000") }
            var to by remember(bg) { mutableStateOf(bg.to ?: "#FF0000") }
            var freq by remember(bg) { mutableStateOf(
                when {
                    bg.duration == null -> 5
                    (bg.duration ?: 920) >= 1498L -> 1
                    (bg.duration ?: 920) >= 1333L -> 2
                    (bg.duration ?: 920) >= 1168L -> 3
                    (bg.duration ?: 920) >= 1003L -> 4
                    (bg.duration ?: 920) >= 841L -> 5
                    (bg.duration ?: 920) >= 678L -> 6
                    (bg.duration ?: 920) >= 513L -> 7
                    (bg.duration ?: 920) >= 348L -> 8
                    (bg.duration ?: 920) >= 183L -> 9
                    (bg.duration ?: 920) > 0L -> 10
                    else -> 0
                }
            ) }

            ColorField(
                label = stringResource(R.string.editor_start_color),
                selectedHex = from,
                onSelect = {
                    from = it
                    onUpdate(bg.copy(type = ColorType.GRADIENT, from = it, to = to))
                }
            )
            Spacer(Modifier.height(8.dp))
            ColorField(
                label = stringResource(R.string.editor_end_color),
                selectedHex = to,
                onSelect = {
                    to = it
                    onUpdate(bg.copy(type = ColorType.GRADIENT, from = from, to = it))
                }
            )
            LabeledSlider(
                label = "频率",
                sliderKey = "bgGradientFreq",
                value = freq,
                onValueChange = {
                    freq = it
                    val ms = frequencyToMs(it)
                    onUpdate(bg.copy(type = ColorType.GRADIENT, from = from, to = to, duration = ms))
                }
            )
        }
        else -> {}
    }
}

@Composable
fun AppearanceEditor(
    appearance: Appearance,
    sceneType: SceneType,
    onUpdate: (Appearance) -> Unit
) {
    BackgroundColorEditor(
        bg = appearance.backgroundColor ?: ColorConfig(),
        onUpdate = { onUpdate(appearance.copy(backgroundColor = it)) },
        modes = if (sceneType == SceneType.IMAGE) listOf(ColorType.STATIC) else ColorType.entries.toList()
    )

    DotMaskEditor(
        overlays = appearance.overlay,
        onUpdate = { onUpdate(appearance.copy(overlay = it)) }
    )
}

// ─── Dot Mask Editor ────────────────────────────────────

@Composable
fun DotMaskEditor(
    overlays: List<Overlay>?,
    onUpdate: (List<Overlay>?) -> Unit
) {
    val overlay = overlays?.firstOrNull { it.type == OverlayType.DOT_MASK }
    var hasOverlay by remember(overlays) { mutableStateOf(overlay != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(stringResource(R.string.editor_dot_mask))
        Spacer(Modifier.weight(1f))
        Switch(
            checked = hasOverlay,
            onCheckedChange = {
                hasOverlay = it
                onUpdate(if (it) listOf(Overlay(type = OverlayType.DOT_MASK)) else null)
            }
        )
    }
    if (hasOverlay) {
        val dot = overlay ?: Overlay()
        var dotSize by remember(dot) { mutableStateOf(dot.size.coerceIn(1, 10)) }
        var dotStyle by remember(dot) { mutableStateOf(dot.style) }

        FieldLabel(stringResource(R.string.editor_mask_style))
        TypeSelector(
            options = listOf("round" to stringResource(R.string.editor_mask_round), "square" to stringResource(R.string.editor_mask_square), "heart" to stringResource(R.string.editor_mask_heart)),
            selected = dotStyle,
            onSelect = {
                dotStyle = it
                onUpdate(listOf(dot.copy(style = it)))
            }
        )
        LabeledSlider(
            label = stringResource(R.string.editor_dot_size),
            sliderKey = "dotSize",
            value = dotSize,
            range = 1..10,
            onValueChange = {
                dotSize = it
                onUpdate(listOf(dot.copy(size = it)))
            }
        )
    }
}

// ─── Labels ──────────────────────────────────────────────

@Composable
private fun idleSceneTypeLabel(type: IdleSceneType): String = when (type) {
    IdleSceneType.NONE -> stringResource(R.string.idle_none)
    IdleSceneType.CLOCK -> stringResource(R.string.idle_clock)
    IdleSceneType.COUNTDOWN -> stringResource(R.string.idle_countdown)
    IdleSceneType.IMAGE -> stringResource(R.string.idle_image)
}

@Composable
private fun sceneTypeLabel(type: SceneType): String = when (type) {
    SceneType.TEXT -> stringResource(R.string.scene_text)
    SceneType.IMAGE -> stringResource(R.string.scene_image)
    SceneType.VIDEO -> stringResource(R.string.scene_video)
}

private fun copyToAppStorage(context: android.content.Context, uri: android.net.Uri, prefix: String): String {
    val ext = context.contentResolver.getType(uri)?.let { mime ->
        when {
            mime.startsWith("image/") -> mime.substringAfter("/").let { if (it == "jpeg") "jpg" else it }
            mime.startsWith("video/") -> mime.substringAfter("/")
            else -> null
        }
    } ?: "bin"
    val name = "${prefix}_${System.currentTimeMillis()}.$ext"
    val dest = java.io.File(context.filesDir, "media/$name")
    dest.parentFile?.mkdirs()
    context.contentResolver.openInputStream(uri)?.use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
    return dest.absolutePath
}

@Composable
private fun transitionTypeLabel(type: TransitionType): String = when (type) {
    TransitionType.NONE -> stringResource(R.string.transition_none)
    TransitionType.FADE_IN -> stringResource(R.string.transition_fade_in)
    TransitionType.SLIDE_IN_FROM_LEFT -> stringResource(R.string.transition_slide_left)
    TransitionType.SLIDE_IN_FROM_RIGHT -> stringResource(R.string.transition_slide_right)
    TransitionType.SLIDE_IN_FROM_TOP -> stringResource(R.string.transition_slide_top)
    TransitionType.SLIDE_IN_FROM_BOTTOM -> stringResource(R.string.transition_slide_bottom)
}

@Composable
private fun scrollDirectionLabel(dir: ScrollDirection): String = when (dir) {
    ScrollDirection.LEFT_TO_RIGHT -> stringResource(R.string.scroll_ltr)
    ScrollDirection.RIGHT_TO_LEFT -> stringResource(R.string.scroll_rtl)
    ScrollDirection.TOP_TO_BOTTOM -> stringResource(R.string.scroll_ttb)
    ScrollDirection.BOTTOM_TO_TOP -> stringResource(R.string.scroll_btt)
}
