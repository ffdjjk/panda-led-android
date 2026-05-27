package com.biexi.pandaled.ui.detail.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biexi.pandaled.data.model.*
import com.biexi.pandaled.ui.detail.EditorTarget
import org.burnoutcrew.reorderable.*

/**
 * Middle column: idleScene (fixed at top) + scenes list (draggable to reorder).
 */
@Composable
fun SceneListColumn(
    idleScene: IdleScene,
    scenes: List<Scene>,
    selectedTarget: EditorTarget,
    highlightedSceneIndex: Int?,
    onSelectIdle: () -> Unit,
    onSelectScene: (Int) -> Unit,
    onMoveScene: (Int, Int) -> Unit,
    onAddScene: (SceneType) -> Unit,
    onDeleteScene: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // waiting scene — fixed, non-draggable
        val isIdleSelected = selectedTarget is EditorTarget.IdleScene
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(onClick = onSelectIdle),
            color = if (isIdleSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                1.dp,
                if (isIdleSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (idleScene.hasMissingAsset()) Color.Red else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(com.biexi.pandaled.R.string.scene_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        idleSceneTypeLabel(idleScene.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (idleScene.hasMissingAsset()) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = stringResource(com.biexi.pandaled.R.string.missing_image),
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Add scene button (inline with idle scene row)
                Box {
                    IconButton(onClick = { addMenuExpanded = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(com.biexi.pandaled.R.string.scene_add), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = addMenuExpanded,
                        onDismissRequest = { addMenuExpanded = false }
                    ) {
                        listOf(
                            com.biexi.pandaled.R.string.scene_text to SceneType.TEXT,
                            com.biexi.pandaled.R.string.scene_image to SceneType.IMAGE,
                            com.biexi.pandaled.R.string.scene_video to SceneType.VIDEO
                        ).forEach { (resId, type) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addMenuExpanded = false; onAddScene(type) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(resId),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Scenes list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(
                items = scenes,
                key = { idx, _ -> "scene_$idx" }
            ) { index, scene ->
                val isSelected = when (selectedTarget) {
                    is EditorTarget.Scene -> selectedTarget.index == index
                    else -> false
                }
                val isHighlighted = highlightedSceneIndex == index
                val isFirst = index == 0
                val isLast = index == scenes.lastIndex

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable(onClick = { onSelectScene(index) })
                        .then(
                            if (isHighlighted) Modifier.border(
                                1.dp,
                                Color.Red,
                                RoundedCornerShape(8.dp)
                            ) else Modifier
                        ),
                    color = when {
                        isHighlighted -> Color.Red.copy(alpha = 0.08f)
                        isSelected -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Scene type icon
                        Icon(
                            imageVector = when (scene.type) {
                                SceneType.TEXT -> Icons.Default.TextFields
                                SceneType.IMAGE -> Icons.Default.Image
                                SceneType.VIDEO -> Icons.Default.Videocam
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (scene.hasMissingAsset()) Color.Red
                                    else MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                scene.name.ifBlank { "场景 ${index + 1}" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${sceneTypeLabel(scene.type)} · ${scene.duration}${stringResource(com.biexi.pandaled.R.string.duration_unit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Warning icon for missing assets
                        if (scene.hasMissingAsset()) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = stringResource(com.biexi.pandaled.R.string.missing_image),
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Up arrow — grayed if first
                        IconButton(
                            onClick = { if (!isFirst) onMoveScene(index, index - 1) },
                            modifier = Modifier.size(24.dp),
                            enabled = !isFirst
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(com.biexi.pandaled.R.string.scene_move_up),
                                modifier = Modifier.size(18.dp),
                                tint = if (isFirst) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Down arrow — grayed if last
                        IconButton(
                            onClick = { if (!isLast) onMoveScene(index, index + 1) },
                            modifier = Modifier.size(24.dp),
                            enabled = !isLast
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(com.biexi.pandaled.R.string.scene_move_down),
                                modifier = Modifier.size(18.dp),
                                tint = if (isLast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Delete button
                        IconButton(
                            onClick = { onDeleteScene(index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(com.biexi.pandaled.R.string.scene_delete),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun idleSceneTypeLabel(type: IdleSceneType): String = when (type) {
    IdleSceneType.NONE -> stringResource(com.biexi.pandaled.R.string.idle_none)
    IdleSceneType.CLOCK -> stringResource(com.biexi.pandaled.R.string.idle_clock)
    IdleSceneType.COUNTDOWN -> stringResource(com.biexi.pandaled.R.string.idle_countdown)
    IdleSceneType.IMAGE -> stringResource(com.biexi.pandaled.R.string.idle_image)
}

@Composable
private fun sceneTypeLabel(type: SceneType): String = when (type) {
    SceneType.TEXT -> stringResource(com.biexi.pandaled.R.string.scene_text)
    SceneType.IMAGE -> stringResource(com.biexi.pandaled.R.string.scene_image)
    SceneType.VIDEO -> stringResource(com.biexi.pandaled.R.string.scene_video)
}
