package com.biexi.pandaled.ui.detail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.biexi.pandaled.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.biexi.pandaled.data.model.*
import kotlinx.coroutines.delay
import com.biexi.pandaled.ui.detail.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    onNavigateToFullScreen: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Load project on first composition
    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    // Preview auto-advance logic
    // Preview: current scene only, no cycling

    // Save when project changes
    LaunchedEffect(state.project) {
        // Debounced auto-save could go here; for now manual save
    }

    var showMenu by remember { mutableStateOf(false) }
    var showSubscribeDialog by remember { mutableStateOf(false) }
    var showAdLoading by remember { mutableStateOf(false) }
    var showNetworkError by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.project?.name ?: "加载中...",
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.selectTab(0) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Play button → subscribe dialog → fullscreen
                    val toastMissingText = stringResource(R.string.toast_missing)
                    IconButton(onClick = {
                        if (state.missingAssets.isNotEmpty()) {
                            android.widget.Toast.makeText(context, toastMissingText, android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.selectTab(1) // switch to scenes tab
                        } else {
                            showSubscribeDialog = true
                        }
                    }) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = stringResource(R.string.detail_fullscreen), modifier = Modifier.size(32.dp))
                    }
                    // Three-dot menu

                }
            )
        }
    ) { padding ->
        if (state.isLoading || state.project == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val project = state.project!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ─── Red warning bar for missing assets ──────
            if (state.missingAssets.isNotEmpty()) {
                MissingAssetBar(
                    missingAssets = state.missingAssets,
                    onClick = { viewModel.navigateToMissingAsset(it) }
                )
            }

            // ─── Preview panel ───────────────────────────
            val currentSceneName = when (val t = state.selectedTarget) {
                is EditorTarget.None -> ""
                is EditorTarget.IdleScene -> stringResource(R.string.scene_waiting)
                is EditorTarget.Scene -> project.scenes.getOrNull(t.index)?.name ?: "场景 ${t.index + 1}"
            }
            val configuration = LocalConfiguration.current
            val previewAspectRatio = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
                val longSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
                val shortSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
                longSide.toFloat() / shortSide.toFloat()
            }
            PreviewPanel(
                project = project,
                currentPreviewIndex = state.previewCurrentIndex,
                isPlaying = state.isPreviewPlaying,
                replayKey = state.previewReplayKey,
                sceneName = currentSceneName,
                selectedTarget = state.selectedTarget,
                onAutoAdvance = { viewModel.triggerScenePreview(state.selectedTarget) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .aspectRatio(previewAspectRatio)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ─── Tab bar ─────────────────────────────────
            val tabTitles = listOf(stringResource(R.string.detail_tab_info), stringResource(R.string.detail_tab_scenes), stringResource(R.string.detail_tab_editor))
            var selectedTab by remember { mutableIntStateOf(2) }
            var infoHighlight by remember { mutableIntStateOf(0) }

            // React to tab switch request from ViewModel
            LaunchedEffect(state.tabToSwitch) {
                state.tabToSwitch?.let { selectedTab = it; viewModel.clearTabSwitch() }
            }

            // Flash calendar icon when switching to Info tab
            LaunchedEffect(selectedTab) {
                if (selectedTab == 0) infoHighlight++
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── Tab content ─────────────────────────────
            when (selectedTab) {
                0 -> {
                    ProjectInfoColumn(
                        project = project,
                        onUpdate = { name, startTime ->
                            viewModel.updateProjectInfo(name, startTime)
                        },
                        highlightTrigger = infoHighlight,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }
                1 -> {
                    SceneListColumn(
                        idleScene = project.idleScene,
                        scenes = project.scenes,
                        selectedTarget = state.selectedTarget,
                        highlightedSceneIndex = state.highlightedSceneIndex,
                        onSelectIdle = {
                            viewModel.selectIdleScene()
                            selectedTab = 2
                        },
                        onSelectScene = {
                            viewModel.selectScene(it)
                            selectedTab = 2
                        },
                        onMoveScene = { from, to -> viewModel.moveScene(from, to) },
                        onAddScene = { type ->
                            viewModel.addScene(type)
                            selectedTab = 2
                        },
                        onDeleteScene = { viewModel.deleteScene(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                2 -> {
                    SceneEditorColumn(
                        project = project,
                        selectedTarget = state.selectedTarget,
                        highlightedSceneIndex = state.highlightedSceneIndex,
                        onUpdateIdleScene = { viewModel.updateIdleScene(it) },
                        onUpdateScene = { idx, scene -> viewModel.updateScene(idx, scene) },
                        onNavigateToInfo = { viewModel.selectTab(0) },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }
        }
    }

    // ─── QR Share Dialog ─────────────────────────────────
    if (state.showQrDialog && state.qrCodeBitmap != null) {
        Dialog(
            onDismissRequest = {}, // only close button dismisses
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.share_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    // QR image (label baked into bitmap)
                    Image(
                        bitmap = state.qrCodeBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.qr_code_desc),
                        modifier = Modifier
                            .size(260.dp, 280.dp) // taller for label
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(16.dp))
                    // ─── Action buttons ──────────────────
                    val chooserTitle = stringResource(R.string.share_title)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Share via system
                        TextButton(onClick = {
                            val bmp = state.qrCodeBitmap!!
                            val file = java.io.File(context.cacheDir, "shared/qr_share.png")
                            file.parentFile?.mkdirs()
                            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, chooserTitle))
                        }) {
                            Text(stringResource(R.string.share))
                        }
                        // Close
                        TextButton(onClick = { viewModel.dismissQrDialog() }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }



    // ─── Loading overlay during ad load ───────────────
    val activity = context as android.app.Activity
    val prevNavColor = activity.window.navigationBarColor
    val prevStatusColor = activity.window.statusBarColor
    if (showAdLoading) {
        activity.window.navigationBarColor = android.graphics.Color.BLACK
        activity.window.statusBarColor = android.graphics.Color.BLACK
        Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.loading_ads), color = Color.White)
                }
            }
        }
    }

    // ─── Network error overlay ────────────────────────
    if (showNetworkError) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2500)
            showNetworkError = false
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 6.dp
            ) {
                Text(
                    stringResource(R.string.network_error),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    // ─── Subscribe / Start dialog ──────────────────────
    if (showSubscribeDialog) {
        AlertDialog(
            onDismissRequest = { showSubscribeDialog = false },
            title = {
                Text(
                    stringResource(R.string.subscribe_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(R.string.subscribe_hint))
            },
            confirmButton = {
                Button(
                    onClick = {
                            showSubscribeDialog = false
                        viewModel.launchFullScreen(context)
                    },
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Text(
                        stringResource(R.string.subscribe_cta),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSubscribeDialog = false
                    showAdLoading = true
                    com.biexi.pandaled.util.AdManager.loadAndShow(
                        context as android.app.Activity,
                        onDismissed = {
                            showAdLoading = false
                            activity.window.navigationBarColor = prevNavColor
                            activity.window.statusBarColor = prevStatusColor
                            viewModel.launchFullScreen(context)
                        },
                        onNetworkError = {
                            showAdLoading = false
                            activity.window.navigationBarColor = prevNavColor
                            activity.window.statusBarColor = prevStatusColor
                            showNetworkError = true
                        }
                    )
                }) {
                    Text(stringResource(R.string.start_directly))
                }
            }
        )
    }
}

// ─── Missing Asset Warning Bar ───────────────────────────

@Composable
fun MissingAssetBar(
    missingAssets: List<MissingAsset>,
    onClick: (MissingAsset) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFD32F2F)
    ) {
        Row(
            modifier = Modifier
                .clickable {
                    // Click first missing asset
                    if (missingAssets.isNotEmpty()) onClick(missingAssets.first())
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            val waitingLabel = stringResource(R.string.scene_waiting)
            val labels = missingAssets.joinToString(", ") { ma ->
                if (ma.label == "idle") waitingLabel else ma.label
            }
            Text(
                stringResource(R.string.missing_assets, labels),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }

}
