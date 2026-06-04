package com.biexi.pandaled.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biexi.pandaled.data.model.ProjectIndex
import androidx.compose.ui.res.stringResource
import com.biexi.pandaled.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.IndexedValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onProjectClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val projects by viewModel.projects.collectAsState()

    var isCreating by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<com.biexi.pandaled.data.model.ProjectIndex?>(null) }

    var fabExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogoOverlay by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsScreen(onBack = { showSettings = false })
        } else if (isCreating) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.creating_project), color = Color.White)
                }
            }
        } else {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { showLogoOverlay = true }) {
                        // Animated GIF logo
                        AsyncImage(
                            model = R.raw.logo,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.app_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                actions = {
                    // Dark / Light theme toggle
                    val ctxActions = LocalContext.current
                    val prefs = remember(ctxActions) {
                        ctxActions.getSharedPreferences("pandaled_prefs", android.content.Context.MODE_PRIVATE)
                    }
                    var colorMode by remember(prefs) {
                        mutableStateOf(prefs.getString("color_mode", "system") ?: "system")
                    }
                    DisposableEffect(prefs) {
                        val listener =
                            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                                if (key == "color_mode") {
                                    colorMode = prefs.getString("color_mode", "system") ?: "system"
                                }
                            }
                        prefs.registerOnSharedPreferenceChangeListener(listener)
                        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                    }
                    val effectiveDark = when (colorMode) {
                        "dark" -> true
                        "light" -> false
                        else -> isSystemInDarkTheme()
                    }
                    IconButton(onClick = {
                        val next = if (effectiveDark) "light" else "dark"
                        prefs.edit().putString("color_mode", next).apply()
                    }) {
                        Icon(
                            imageVector = if (effectiveDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (effectiveDark) stringResource(R.string.home_theme_switch_light) else stringResource(R.string.home_theme_switch_dark)
                        )
                    }
                    // Settings
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    isCreating = true
                    viewModel.createNewProject { id ->
                        onProjectClick(id)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_new_project))
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.home_no_projects),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(projects, key = { _, it -> it.id }) { index, project ->
                    val isFirst = index == 0
                    val isLast = index == projects.lastIndex
                    ProjectCard(
                        project = project,
                        isFirst = isFirst,
                        isLast = isLast,
                        onMoveUp = if (!isFirst) {
                            { viewModel.moveProject(project, projects[index - 1]) }
                        } else null,
                        onMoveDown = if (!isLast) {
                            { viewModel.moveProject(project, projects[index + 1]) }
                        } else null,
                        onClick = { onProjectClick(project.id) },
                        onDelete = { projectToDelete = project }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(stringResource(R.string.home_delete_title)) },
            text = { Text(stringResource(R.string.home_delete_confirm, project.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(project)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.home_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text(stringResource(R.string.home_cancel))
                }
            }
        )
    }

        // Logo overlay — fullscreen MP4 player with two-phase animation
        if (showLogoOverlay) {
            LogoOverlay(
                onDismissed = { showLogoOverlay = false }
            )
        }
        } // end else
    } // end Box

}

@Composable
fun ProjectCard(
    project: ProjectIndex,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name.ifBlank { stringResource(R.string.untitled_project) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(project.lastModified)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Up/down + delete in a row
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onMoveUp?.invoke() },
                    enabled = onMoveUp != null,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移",
                        modifier = Modifier.size(18.dp),
                        tint = if (onMoveUp != null) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                }
                IconButton(
                    onClick = { onMoveDown?.invoke() },
                    enabled = onMoveDown != null,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移",
                        modifier = Modifier.size(18.dp),
                        tint = if (onMoveDown != null) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ─── Logo Overlay ───────────────────────────────────────

@Composable
fun LogoOverlay(
    onDismissed: () -> Unit
) {
    val ctx = LocalContext.current

    var isExiting by remember { mutableStateOf(false) }
    var showVideo by remember { mutableStateOf(false) }  // video appears only after bg is ready
    val bgAlpha = remember { Animatable(0f) }

    // Enter: fade in background to 90% over 1s → show video
    LaunchedEffect(Unit) {
        bgAlpha.animateTo(0.9f, animationSpec = tween(300))
        showVideo = true
    }

    // Exit: hide video → fade out background over 0.3s
    LaunchedEffect(isExiting) {
        if (isExiting) {
            showVideo = false
            bgAlpha.animateTo(0f, animationSpec = tween(300))
            onDismissed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha.value))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { if (!isExiting) isExiting = true },
        contentAlignment = Alignment.Center
    ) {
        if (showVideo) {
            AsyncImage(
                model = R.raw.logo,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 300.dp, height = 300.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
        }
    }
}
