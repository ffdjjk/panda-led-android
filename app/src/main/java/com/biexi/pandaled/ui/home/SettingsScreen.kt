package com.biexi.pandaled.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biexi.pandaled.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pandaled_prefs", android.content.Context.MODE_PRIVATE) }

    val systemLang = java.util.Locale.getDefault().language
    var selectedLang by remember { mutableStateOf(prefs.getString("language", "") ?: "") }
    // Default to en if not set
    if (selectedLang.isEmpty()) selectedLang = "en"
    var selectedColorMode by remember { mutableStateOf(prefs.getString("color_mode", "system") ?: "system") }

    var langExpanded by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }

    val langOptions = mapOf(
        "zh" to stringResource(R.string.settings_lang_zh),
        "en" to stringResource(R.string.settings_lang_en)
    )
    val colorModeOptions = mapOf(
        "system" to stringResource(R.string.settings_theme_system),
        "light" to stringResource(R.string.settings_theme_light),
        "dark" to stringResource(R.string.settings_theme_dark)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Language ────────────────────────────────
            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it }
            ) {
                OutlinedTextField(
                    value = langOptions[selectedLang] ?: stringResource(R.string.settings_lang_en),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false }
                ) {
                    langOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                langExpanded = false
                                selectedLang = key
                                prefs.edit().putString("language", key).apply()
                                // Apply locale via AppCompatDelegate then recreate
                                val locale = when (key) {
                                    "zh" -> java.util.Locale("zh")
                                    "en" -> java.util.Locale("en")
                                    else -> java.util.Locale("en")
                                }
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                    }
                }
            }

            // ─── Theme ───────────────────────────────────
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            ExposedDropdownMenuBox(
                expanded = colorExpanded,
                onExpandedChange = { colorExpanded = it }
            ) {
                OutlinedTextField(
                    value = colorModeOptions[selectedColorMode] ?: stringResource(R.string.settings_theme_system),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = colorExpanded,
                    onDismissRequest = { colorExpanded = false }
                ) {
                    colorModeOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                colorExpanded = false
                                selectedColorMode = key
                                prefs.edit().putString("color_mode", key).apply()
                            }
                        )
                    }
                }
            }

            // ─── Cache ───────────────────────────────────
            Text(stringResource(R.string.settings_cache), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            val cacheSize = remember { calculateCacheSize(context) }
            var cacheText by remember { mutableStateOf("计算中...") }
            LaunchedEffect(Unit) {
                cacheText = if (cacheSize <= 0) "0 B"
                else when {
                    cacheSize < 1024 -> "${cacheSize} B"
                    cacheSize < 1024 * 1024 -> "${cacheSize / 1024} KB"
                    else -> String.format("%.1f MB", cacheSize / (1024.0 * 1024.0))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_cache_unused) + ": $cacheText",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    clearUnusedCache(context)
                    cacheText = "0 B"
                }) {
                    Text(stringResource(R.string.settings_cache_clear))
                }
            }
        }
    }
}

private fun collectReferencedPaths(context: android.content.Context): Set<String> {
    val referenced = mutableSetOf<String>()
    try {
        val projectsDir = java.io.File(context.filesDir, "projects")
        if (projectsDir.exists()) {
            val gson = com.google.gson.Gson()
            projectsDir.listFiles()?.filter { it.extension == "json" }?.forEach { f ->
                try {
                    val project = gson.fromJson(f.readText(), com.biexi.pandaled.data.model.Project::class.java)
                    project?.scenes?.forEach { scene ->
                        scene.content.source?.let { s -> if (s.isNotBlank()) referenced.add(s) }
                    }
                    project?.idleScene?.content?.source?.let { s -> if (s.isNotBlank()) referenced.add(s) }
                } catch (_: Exception) {}
            }
        }
    } catch (_: Exception) {}
    return referenced
}

private fun calculateCacheSize(context: android.content.Context): Long {
    val mediaDir = java.io.File(context.filesDir, "media")
    if (!mediaDir.exists()) return 0L
    val referenced = collectReferencedPaths(context)
    var unusedBytes = 0L
    mediaDir.walkTopDown().filter { it.isFile }.forEach { f ->
        if (f.absolutePath !in referenced) {
            unusedBytes += f.length()
        }
    }
    return unusedBytes
}

private fun clearUnusedCache(context: android.content.Context) {
    val mediaDir = java.io.File(context.filesDir, "media")
    if (!mediaDir.exists()) return
    val referenced = collectReferencedPaths(context)
    mediaDir.walkTopDown().filter { it.isFile }.forEach { f ->
        if (f.absolutePath !in referenced) {
            f.delete()
        }
    }
}
