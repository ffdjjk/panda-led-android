package com.biexi.pandaled.ui.detail.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biexi.pandaled.data.model.Project
import androidx.compose.ui.res.stringResource
import com.biexi.pandaled.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectInfoColumn(
    project: Project,
    onUpdate: (name: String, startTime: String) -> Unit,
    highlightTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    var editName by remember(project.name) { mutableStateOf(project.name) }
    var editStartTime by remember(project.startTime) { mutableStateOf(project.startTime) }

    // Date/Time picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Flash animation on highlightTrigger
    val calendarScale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(highlightTrigger) {
        if (highlightTrigger > 0) {
            calendarScale.animateTo(1.3f, animationSpec = tween(150))
            calendarScale.animateTo(1f, animationSpec = tween(150))
        }
    }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    // Parse current startTime into Calendar
    val calendar = remember(editStartTime) {
        try {
            val parsed = dateFormat.parse(editStartTime)
            Calendar.getInstance().apply { if (parsed != null) time = parsed }
        } catch (_: Exception) {
            Calendar.getInstance()
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = calendar.timeInMillis
    )
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = true
    )

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        calendar.timeInMillis = millis
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) {
                    Text("下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.pick_time)) },
            text = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    calendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    calendar.set(Calendar.MINUTE, timePickerState.minute)
                    calendar.set(Calendar.SECOND, 0)
                    editStartTime = dateFormat.format(calendar.time)
                    onUpdate(editName, editStartTime)
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.home_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        )
    }

    Card(
        modifier = modifier.padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.detail_tab_info),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = editName,
                onValueChange = { newName ->
                    editName = newName
                    onUpdate(newName, editStartTime)
                },
                label = { Text(stringResource(R.string.detail_project_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = editStartTime,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.detail_start_time)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Text("📅", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.scale(calendarScale.value))
                    }
                }
            )
        }
    }
}
