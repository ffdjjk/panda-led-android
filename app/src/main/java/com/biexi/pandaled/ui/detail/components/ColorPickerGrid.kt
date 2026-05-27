package com.biexi.pandaled.ui.detail.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.biexi.pandaled.R
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

// ─── Preset colors ───────────────────────────────────────

val presetColors = buildList {
    // 20-step gray gradient: #FFFFFF → #000000
    for (i in 0..19) {
        val v = (255 * (19 - i) / 19)
        val hex = String.format("#%02X%02X%02X", v, v, v)
        add(hex to Color(v, v, v))
    }
    // Then rainbow colors
    addAll(listOf(
        "#FF0000" to Color.Red,
        "#FF8800" to Color(0xFFFF8800),
        "#FFFF00" to Color.Yellow,
        "#88FF00" to Color(0xFF88FF00),
        "#00FF00" to Color(0xFF00FF00),
        "#00FF88" to Color(0xFF00FF88),
        "#00FFFF" to Color.Cyan,
        "#0088FF" to Color(0xFF0088FF),
        "#0000FF" to Color.Blue,
        "#8800FF" to Color(0xFF8800FF),
        "#FF00FF" to Color.Magenta,
        "#FF0088" to Color(0xFFFF0088),
    ))
}

// Recent color memory
private val recentColors = mutableListOf<String>()

private fun addRecent(hex: String) {
    recentColors.remove(hex)
    recentColors.add(0, hex)
    if (recentColors.size > 10) recentColors.removeAt(recentColors.lastIndex)
}

fun hexToColor(hex: String): Color {
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Black }
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

// ─── ColorField: clickable preview → opens picker ─────────

@Composable
fun ColorField(
    label: String,
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        // Color preview swatch only (no hex text outside)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(hexToColor(selectedHex))
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        ColorPickerDialog(
            initialHex = selectedHex,
            onDismiss = { showPicker = false },
            onSelect = {
                addRecent(it)
                onSelect(it)
                showPicker = false
            }
        )
    }
}

// ─── ColorPickerDialog ────────────────────────────────────

@Composable
fun ColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val initialColor = hexToColor(initialHex)
    val initHsv = remember {
        val arr = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (initialColor.red * 255).toInt(),
            (initialColor.green * 255).toInt(),
            (initialColor.blue * 255).toInt(),
            arr
        )
        arr
    }
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var val_ by remember { mutableFloatStateOf(initHsv[2]) }
    var pickedColor by remember { mutableStateOf(initialColor) }

    // Sync HSV from a Color (called when color changes from hex/recent)
    fun syncHsv(c: Color) {
        val arr = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt(),
            arr
        )
        hue = arr[0]; sat = arr[1]; val_ = arr[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // ─── Row 1: Hue slider (rainbow) ──────────
                HueSlider(
                    hue = hue,
                    onHueChanged = { newHue ->
                        hue = newHue
                        pickedColor = hsvToColor(hue, sat, val_)
                    },
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )

                Spacer(Modifier.height(10.dp))

                // ─── Row 2: SV square ────────────────────
                SaturationValueSquare(
                    hue = hue,
                    currentColor = pickedColor,
                    sat = sat,
                    val_ = val_,
                    onSatValChanged = { newSat, newVal ->
                        sat = newSat; val_ = newVal
                        pickedColor = hsvToColor(hue, sat, val_)
                    },
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )

                // ─── Row 3: Color circle + editable Hex ───
                val currentHex = colorToHex(pickedColor)
                var hexInput by remember { mutableStateOf(currentHex.removePrefix("#").uppercase()) }
                var hexError by remember { mutableStateOf(false) }
                LaunchedEffect(pickedColor) {
                    hexInput = colorToHex(pickedColor).removePrefix("#")
                    hexError = false
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(pickedColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { v ->
                            val upper = v.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }.take(6).uppercase()
                            hexInput = upper
                            if (upper.length == 6) {
                                val parsed = try { android.graphics.Color.parseColor("#$upper") } catch (_: Exception) { null }
                                if (parsed != null) {
                                    val newColor = Color(parsed)
                                    pickedColor = newColor
                                    syncHsv(newColor)
                                    hexError = false
                                } else hexError = true
                            } else hexError = upper.isNotEmpty()
                        },
                        isError = hexError,
                        supportingText = if (hexError) {{ Text(stringResource(R.string.invalid_hex), color = MaterialTheme.colorScheme.error, fontSize = 10.sp) }} else null,
                        label = { Text("hex") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }


            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(colorToHex(pickedColor)) }) {
                Text(stringResource(R.string.home_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_cancel))
            }
        }
    )
}

// ─── Horizontal row of color swatches ─────────────────────

@Composable
private fun ColorRow(
    hexList: List<String>,
    selectedColor: Color,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        hexList.forEach { hex ->
            val color = hexToColor(hex)
            val isSelected = hex.equals(colorToHex(selectedColor), ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onClick(hex) }
            )
        }
    }
}

// ─── HSV color wheel ──────────────────────────────────────

@Composable
fun HsvWheel(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val hsv = remember { floatArrayOf(0f, 0f, 0f) }
    val initialHsv = remember(initialColor) {
        val c = initialColor
        floatArrayOf(0f, 0f, 0f).also { android.graphics.Color.RGBToHSV(
            (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), it
        )}
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }

    LaunchedEffect(hue, sat) {
        hsv[0] = hue; hsv[1] = sat; hsv[2] = 1f
        val argb = android.graphics.Color.HSVToColor(hsv)
        onColorChanged(Color(argb))
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateHsv(offset, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), { hue = it }, { sat = it })
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.pressed }
                        if (change != null) {
                            updateHsv(change.position, androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()), { hue = it }, { sat = it })
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val wheelRadius = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val stepDeg = 2f
        val stepR = 4f

        // Draw hue wheel
        var angle = 0f
        while (angle <= 360f) {
            val rad = angle * PI / 180f
            var r = 0f
            while (r <= wheelRadius) {
                val currentSat = (r / wheelRadius).coerceIn(0f, 1f)
                val color = Color.hsv(angle, currentSat, 1f)
                val px = cx + r * cos(rad).toFloat()
                val py = cy + r * sin(rad).toFloat()
                drawCircle(color, radius = 3f, center = Offset(px, py))
                r += stepR
            }
            angle += stepDeg
        }

        // Draw cursor
        val cursorRad = (sat * wheelRadius).coerceAtMost(wheelRadius - 4f)
        val cursorAngle = (hue * PI / 180f).toFloat()
        val cursorX = cx + cursorRad * cos(cursorAngle)
        val cursorY = cy + cursorRad * sin(cursorAngle)
        drawCircle(Color.White, radius = 7f, center = Offset(cursorX, cursorY))
        drawCircle(Color.Black, radius = 5f, center = Offset(cursorX, cursorY))
        drawCircle(Color.White, radius = 3f, center = Offset(cursorX, cursorY))
    }
}

private fun updateHsv(
    position: Offset,
    size: Size,
    setHue: (Float) -> Unit,
    setSat: (Float) -> Unit
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = position.x - cx
    val dy = position.y - cy
    val dist = sqrt(dx * dx + dy * dy)
    val wheelRadius = min(size.width, size.height) / 2f
    val sat = (dist / wheelRadius).coerceIn(0f, 1f)
    val angle = atan2(dy.toDouble(), dx.toDouble()).let { rad ->
        Math.toDegrees(rad).toFloat().let { if (it < 0) it + 360f else it }
    }
    setSat(sat)
    setHue(angle)
}

// ─── NumberStepper ─────────────────────────────────────

/** Slider with label: name on left, slider + value on right. */
@Composable
fun LabeledSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 1..10,
    modifier: Modifier = Modifier,
    sliderKey: String = label
) {
    key(sliderKey) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 68.dp)
            )
            Text(
                value.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Numeric stepper: left −, center value, right +. */
@Composable
fun NumberStepper(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    min: Int = 1,
    max: Int = 200,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onValueChange(((value.toFloatOrNull()?.toInt() ?: min) - 1).coerceAtLeast(min).toString()) },
            modifier = Modifier.size(34.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 42.dp),
            textAlign = TextAlign.Center
        )
        FilledTonalIconButton(
            onClick = { onValueChange(((value.toFloatOrNull()?.toInt() ?: min) + 1).coerceAtMost(max).toString()) },
            modifier = Modifier.size(34.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ─── ColorPickerGrid (backward compatibility) ─────────

@Composable
fun ColorPickerGrid(
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val recent = recentColors.toList() + presetColors.map { it.first }
    val unique = recent.distinct().take(20)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val columns = (maxWidth / 38.dp).toInt().coerceAtLeast(4)
        val scroll = rememberScrollState()
        Column(Modifier.verticalScroll(scroll)) {
            var rowItems = 0
            var currentRow = mutableListOf<String>()
            unique.forEach { hex ->
                currentRow.add(hex)
                rowItems++
                if (rowItems >= columns) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        currentRow.forEach { h ->
                            val color = hexToColor(h)
                            val isSel = h.equals(selectedHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                                    .border(
                                        width = if (isSel) 3.dp else 1.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onSelect(h) }
                            )
                        }
                    }
                    currentRow = mutableListOf()
                    rowItems = 0
                }
            }
            if (currentRow.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    currentRow.forEach { h ->
                        val color = hexToColor(h)
                        val isSel = h.equals(selectedHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .border(
                                    width = if (isSel) 3.dp else 1.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelect(h) }
                        )
                    }
                }
            }
        }
    }
}

// ─── Saturation-Value square for fixed hue ────────────────

@Composable
private fun SaturationValueSquare(
    hue: Float,
    currentColor: Color,
    sat: Float,
    val_: Float,
    onSatValChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val pointerPos = Offset(sat, 1f - val_)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val w = size.width.toFloat()
                    val ht = size.height.toFloat()
                    val pos = change.position
                    val x = (pos.x / w).coerceIn(0f, 1f)
                    val y = (pos.y / ht).coerceIn(0f, 1f)
                    val newSat = x
                    val newVal = 1f - y
                    onSatValChanged(newSat, newVal)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val w = size.width.toFloat()
                    val ht = size.height.toFloat()
                    val x = (pos.x / w).coerceIn(0f, 1f)
                    val y = (pos.y / ht).coerceIn(0f, 1f)
                    val newSat = x
                    val newVal = 1f - y
                    onSatValChanged(newSat, newVal)
                }
            }
    ) {
        // Draw SV gradient using Compose brushes (no per-frame bitmap alloc)
        val pureHue = hsvToColor(hue, 1f, 1f)
        // Fill with pure hue
        drawRect(pureHue)
        // Horizontal white fade (saturation 0→1)
        drawRect(
            Brush.horizontalGradient(listOf(Color.White, Color.Transparent))
        )
        // Vertical black fade (value 1→0)
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
        )

        // Draw pointer circle
        val cx = pointerPos.x * size.width
        val cy = pointerPos.y * size.height
        val pointerColor = if (pointerPos.y > 0.5f) Color.White else Color.Black
        drawCircle(Color.Transparent, radius = 12f, center = Offset(cx, cy),
            style = Stroke(width = 3f))
        drawCircle(pointerColor, radius = 10f, center = Offset(cx, cy))
        drawCircle(Color.Transparent, radius = 7f, center = Offset(cx, cy),
            style = Stroke(width = 1.5f))
    }
}

private fun hsvToColor(hue: Float, sat: Float, value: Float): Color {
    val arr = floatArrayOf(hue, sat, value)
    val rgb = android.graphics.Color.HSVToColor(arr)
    return Color(rgb)
}

// ─── Hue Slider ───────────────────────────────────────────

@Composable
private fun HueSlider(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // pointerPosition: fraction 0..1
    var pointerFrac by remember { mutableFloatStateOf(hue / 360f) }
    LaunchedEffect(hue) { pointerFrac = hue / 360f }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val f = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                    pointerFrac = f
                    onHueChanged(f * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val f = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    pointerFrac = f
                    onHueChanged(f * 360f)
                }
            }
    ) {
        // Rainbow hue gradient using 7 stops
        drawRect(
            Brush.horizontalGradient(
                listOf(
                    Color.Red,
                    Color(0xFFFF8800), // orange
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                )
            )
        )

        // Pointer: filled white circle, clamped to stay fully visible
        val r = size.height * 0.50f
        val px = (pointerFrac * size.width).coerceIn(r, size.width - r)
        val py = size.height / 2f
        drawCircle(Color.White, r, Offset(px, py))
        drawCircle(Color.Black, r, Offset(px, py), style = Stroke(width = 2f))
    }
}

// ─── Long-press repeat modifier ──────────────────────────

fun Modifier.longPressRepeatable(
    scope: kotlinx.coroutines.CoroutineScope,
    onClick: () -> Unit
): Modifier = this.pointerInput(Unit) {
    var job: kotlinx.coroutines.Job? = null
    detectTapGestures(
        onPress = {
            onClick()
            var delayMs = 400L
            job = scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(delayMs)
                    onClick()
                    delayMs = 80L // accelerate after first repeat
                }
            }
            tryAwaitRelease()
            job?.cancel()
        }
    )
}
