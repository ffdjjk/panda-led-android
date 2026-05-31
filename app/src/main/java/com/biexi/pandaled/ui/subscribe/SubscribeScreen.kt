package com.biexi.pandaled.ui.subscribe

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.biexi.pandaled.R
import com.biexi.pandaled.util.BillingManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscribeScreen(
    onBack: () -> Unit,
    onStartWithAd: () -> Unit,
    onSubscribed: () -> Unit
) {
    val context = LocalContext.current
    val isSubscribed by BillingManager.isSubscribed.collectAsState()
    var isPurchasing by remember { mutableStateOf(false) }
    var purchaseError by remember { mutableStateOf<String?>(null) }

    // Reset purchasing state when user returns from Google Play billing UI
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPurchasing = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // If user becomes subscribed, navigate away
    LaunchedEffect(isSubscribed) {
        if (isSubscribed) {
            onSubscribed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { BillingManager.querySubscriptionStatus() }) {
                        Text(
                            stringResource(R.string.subscribe_restore_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Spacer(Modifier.height(12.dp))

            // ─── Logo (mp4) ──────────────────────
            val ctx = LocalContext.current
            val logoPlayer = remember {
                ExoPlayer.Builder(ctx).build().apply {
                    val uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.logo}")
                    setMediaItem(MediaItem.fromUri(uri))
                    volume = 0f
                    prepare()
                }
            }
            LaunchedEffect(Unit) {
                logoPlayer.playWhenReady = true
                while (true) {
                    logoPlayer.seekTo(0)
                    logoPlayer.playWhenReady = true
                    delay(5_000)
                }
            }
            DisposableEffect(Unit) {
                onDispose {
                    logoPlayer.playWhenReady = false
                    logoPlayer.release()
                }
            }
            AndroidView(
                factory = { viewCtx ->
                    (android.view.LayoutInflater.from(viewCtx)
                        .inflate(R.layout.view_video_player_texture, null) as PlayerView).apply {
                        player = logoPlayer
                        useController = false
                    }
                },
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.height(8.dp))

            // ─── Title ──────────────────────────────
            Text(
                stringResource(R.string.subscribe_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // ─── Price (polling) ───────────────────
            val price by BillingManager.subscriptionPrice.collectAsState()
            LaunchedEffect(Unit) {
                BillingManager.queryProductDetails()
                while (BillingManager.subscriptionPrice.value == null) {
                    delay(3000)
                    BillingManager.queryProductDetails()
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Benefits ───────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    BenefitItem(
                        icon = Icons.Default.NotificationsOff,
                        title = stringResource(R.string.subscribe_benefit_adfree),
                        description = stringResource(R.string.subscribe_benefit_adfree_desc),
                        delayMs = 100
                    )
                    Spacer(Modifier.height(16.dp))
                    BenefitItem(
                        icon = Icons.Default.CalendarMonth,
                        title = stringResource(R.string.subscribe_benefit_cancel),
                        description = stringResource(R.string.subscribe_benefit_cancel_desc),
                        delayMs = 400
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Price + Subscribe ─────────────────
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                // Left: monthly price
                if (price != null) {
                    Text(
                        stringResource(R.string.subscribe_per_month_prefix) + price!!,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.subscribe_price_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Right: subscribe button
                Button(
                    onClick = {
                        isPurchasing = true
                        purchaseError = null
                        val result = BillingManager.launchSubscription(context as Activity)
                        if (result == null) {
                            isPurchasing = false
                            purchaseError = context.resources.getString(R.string.subscribe_error_unavailable)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isPurchasing
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            stringResource(R.string.subscribe_cta),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            }

            // ─── Purchase error ────────────────────
            purchaseError?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // ─── Or divider ──────────────────────────
            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.subscribe_or),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(8.dp))

            // ─── Start with ad ─────────────────────
            OutlinedButton(
                onClick = onStartWithAd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.start_directly))
            }

            Spacer(Modifier.height(20.dp))

            Spacer(Modifier.height(16.dp))
        }
        }
    }
}

@Composable
private fun BenefitItem(
    icon: ImageVector,
    title: String,
    description: String,
    delayMs: Int
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (description.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
