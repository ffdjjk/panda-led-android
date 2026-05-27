package com.biexi.pandaled.ui.detail.components

import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.biexi.pandaled.PandaLedApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biexi.pandaled.R

/**
 * Available system fonts for LED display.
 * Each entry: (key used in JSON, display label, Android Typeface)
 */
data class FontOption(
    val key: String,
    val label: String,
    val typeface: Typeface
)

// Free Chinese fonts — bundled assets, all OFL licensed
// Asset fonts use lazy placeholder; real Typeface loaded via typefaceForKey()
val zhFonts = listOf(
    FontOption("sans-serif", "font_default", Typeface.DEFAULT),
    FontOption("sans-serif-bold", "font_bold", Typeface.DEFAULT_BOLD),
    FontOption("sans-serif-light", "font_light", Typeface.create("sans-serif-light", Typeface.NORMAL)),
    FontOption("serif", "font_serif", Typeface.SERIF),
    FontOption("monospace", "font_mono", Typeface.MONOSPACE),
    FontOption("zcool", "font_zcool", Typeface.DEFAULT), // placeholder, real typeface in typefaceForKey
    FontOption("mashan", "font_mashan", Typeface.DEFAULT), // placeholder
)

// English / Latin fonts
val enFonts = listOf(
    FontOption("sans-serif", "font_default", Typeface.DEFAULT),
    FontOption("sans-serif-bold", "font_bold", Typeface.DEFAULT_BOLD),
    FontOption("sans-serif-light", "font_light", Typeface.create("sans-serif-light", Typeface.NORMAL)),
    FontOption("sans-serif-condensed", "font_condensed", Typeface.create("sans-serif-condensed", Typeface.NORMAL)),
    FontOption("serif", "font_serif", Typeface.SERIF),
    FontOption("monospace", "font_mono", Typeface.MONOSPACE),
    FontOption("casual", "font_casual", Typeface.create("casual", Typeface.NORMAL)),
    FontOption("cursive", "font_cursive", Typeface.create("cursive", Typeface.NORMAL)),
)

/** Active font list based on app locale. */
@Composable
fun localeFonts(): List<FontOption> {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return if (locale.language.startsWith("zh")) zhFonts else enFonts
}

/** Look up a Typeface by font key. */
fun typefaceForKey(key: String?): Typeface {
    // Resolve asset fonts lazily (avoids crash when PandaLedApp not yet initialized)
    return when (key) {
        "zcool" -> assetTypeface("fonts/ZCOOLQingKeHuangYou-Regular.ttf")
        "mashan" -> assetTypeface("fonts/MaShanZheng-Regular.ttf")
        else -> {
            val all = zhFonts + enFonts
            all.firstOrNull { it.key == key }?.typeface ?: Typeface.DEFAULT
        }
    }
}

private var assetTypefaceCache: MutableMap<String, Typeface> = mutableMapOf()

private fun assetTypeface(path: String): Typeface {
    return assetTypefaceCache.getOrPut(path) {
        try {
            Typeface.createFromAsset(PandaLedApp.instance.assets, path)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }
}

/** Horizontal row of font selector buttons, each showing "Abc" in that font. */
@Composable
fun FontFamilySelector(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val labelMap = mapOf(
        "font_default" to stringResource(R.string.font_default),
        "font_bold" to stringResource(R.string.font_bold),
        "font_light" to stringResource(R.string.font_light),
        "font_condensed" to stringResource(R.string.font_condensed),
        "font_serif" to stringResource(R.string.font_serif),
        "font_mono" to stringResource(R.string.font_mono),
        "font_casual" to stringResource(R.string.font_casual),
        "font_cursive" to stringResource(R.string.font_cursive),
        "font_zcool" to stringResource(R.string.font_zcool),
        "font_mashan" to stringResource(R.string.font_mashan),
    )
    val previewText = stringResource(R.string.font_preview)

    val fonts = localeFonts()
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        fonts.forEach { font ->
            val isSelected = font.key == selectedKey
            OutlinedButton(
                onClick = { onSelect(font.key) },
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(
                        text = previewText,
                        fontFamily = when (font.key) {
                            "sans-serif" -> FontFamily.SansSerif
                            "serif" -> FontFamily.Serif
                            "monospace" -> FontFamily.Monospace
                            "cursive" -> FontFamily.Cursive
                            else -> FontFamily.SansSerif
                        },
                        fontWeight = when {
                            font.key.contains("bold") -> FontWeight.Bold
                            font.key.contains("light") -> FontWeight.Light
                            else -> FontWeight.Normal
                        },
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
