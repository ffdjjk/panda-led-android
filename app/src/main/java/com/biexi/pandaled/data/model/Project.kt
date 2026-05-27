package com.biexi.pandaled.data.model

import com.google.gson.annotations.SerializedName

// ─── Enums ───────────────────────────────────────────────

enum class IdleSceneType {
    @SerializedName("none") NONE,
    @SerializedName("clock") CLOCK,
    @SerializedName("countdown") COUNTDOWN,
    @SerializedName("image") IMAGE
}

enum class SceneType {
    @SerializedName("text") TEXT,
    @SerializedName("image") IMAGE,
    @SerializedName("video") VIDEO
}

enum class PlayMode {
    @SerializedName("once") ONCE,
    @SerializedName("loop") LOOP
}

enum class ColorType {
    @SerializedName("static") STATIC,
    @SerializedName("toggle") TOGGLE,
    @SerializedName("gradient") GRADIENT
}

enum class ScrollDirection {
    @SerializedName("leftToRight") LEFT_TO_RIGHT,
    @SerializedName("rightToLeft") RIGHT_TO_LEFT,
    @SerializedName("topToBottom") TOP_TO_BOTTOM,
    @SerializedName("bottomToTop") BOTTOM_TO_TOP

}

enum class TransitionType {
    @SerializedName("none") NONE,
    @SerializedName("fadeIn") FADE_IN,
    @SerializedName("slideInFromLeft") SLIDE_IN_FROM_LEFT,
    @SerializedName("slideInFromRight") SLIDE_IN_FROM_RIGHT,
    @SerializedName("slideInFromTop") SLIDE_IN_FROM_TOP,
    @SerializedName("slideInFromBottom") SLIDE_IN_FROM_BOTTOM
}

enum class OverlayType {
    @SerializedName("dotMask") DOT_MASK
}

// ─── Style ───────────────────────────────────────────────

data class ContentStyle(
    val format: String? = null,        // clock / countdown format
    val fontFamily: String? = "sans-serif",
    val size: Int = 40,
    val color: String? = "#FFFFFF"     // simple hex for idle style
)

// ─── Color Config (static / toggle / gradient) ───────────

data class ColorConfig(
    val type: ColorType = ColorType.STATIC,
    val value: String? = "#FFFFFF",    // static
    val from: String? = "#0000FF",     // toggle / gradient A
    val to: String? = "#FFFF00",       // toggle / gradient B
    val frequency: Int? = null,        // toggle: 1-10
    val duration: Long? = null         // gradient: ms
)

// ─── Transform ───────────────────────────────────────────

data class Transform(
    val mirror: Boolean = false
)

// ─── Motion ──────────────────────────────────────────────

data class Motion(
    val scroll: ScrollConfig? = null
)

data class ScrollConfig(
    val direction: ScrollDirection = ScrollDirection.RIGHT_TO_LEFT,
    val speed: Int = 5,
    val continuous: Boolean = false
)

// ─── Animation ───────────────────────────────────────────

data class Animation(
    val blink: BlinkConfig? = null
)

data class BlinkConfig(
    val frequency: Int = 5             // 0-10, 0=off
)

// ─── Transition ──────────────────────────────────────────

data class Transition(
    val enter: EnterConfig = EnterConfig()
)

data class EnterConfig(
    val type: TransitionType = TransitionType.NONE,
    val duration: Float = 0f           // seconds
)

// ─── Appearance ──────────────────────────────────────────

data class Appearance(
    val overlay: List<Overlay>? = listOf(Overlay()),
    val backgroundColor: ColorConfig? = ColorConfig()
)

data class Overlay(
    val type: OverlayType = OverlayType.DOT_MASK,
    val size: Int = 1,
    val style: String = "round",       // "round" | "square" | "heart"
    val zIndex: Int = 0
)

// ─── Idle Scene ──────────────────────────────────────────

data class IdleScene(
    val type: IdleSceneType = IdleSceneType.COUNTDOWN,
    val content: IdleSceneContent? = null
) {
    /** Returns true if this idle scene references a null/empty source. */
    fun hasMissingAsset(): Boolean {
        if (type == IdleSceneType.IMAGE) {
            return content?.source.isNullOrBlank()
        }
        return false
    }
}

data class IdleSceneContent(
    // clock / countdown shared
    val style: ContentStyle? = null,
    // image
    val source: String? = null         // null = 待完成
)

// ─── Scene ───────────────────────────────────────────────

data class Scene(
    val name: String = "",
    val type: SceneType = SceneType.TEXT,
    val duration: Int = 5,             // seconds
    val playMode: PlayMode = PlayMode.LOOP,
    val content: SceneContent = SceneContent(),
    val transition: Transition = Transition(),
    val appearance: Appearance = Appearance()
) {
    /** Returns true if this scene has a null/empty source for image/video type. */
    fun hasMissingAsset(): Boolean {
        return (type == SceneType.IMAGE || type == SceneType.VIDEO) &&
                content.source.isNullOrBlank()
    }
}

data class SceneContent(
    // text: source is the text string
    // image / video: source is the file path
    val source: String? = null,
    val style: ContentStyle? = null,       // text only
    val color: ColorConfig? = ColorConfig(value = "#FFFFFF"),  // text only
    val transform: Transform? = null,       // text / image / video
    val motion: Motion? = null,             // text only
    val animation: Animation? = null        // text / image
)

// ─── Top-level Project ───────────────────────────────────

/**
 * Map 0-10 frequency to interval in milliseconds.
 */
fun frequencyToMs(frequency: Int): Long {
    val f = frequency.coerceIn(0, 10)
    return when (f) {
        0 -> 0L
        1 -> 1000L
        2 -> 700L
        3 -> 600L
        4 -> 500L
        5 -> 400L
        6 -> 300L
        7 -> 200L
        8 -> 150L
        9 -> 100L
        10 -> 50L
        else -> 50L
    }
}

data class Project(
    val version: String = "1.0.0",
    val name: String = "",
    val startTime: String = "",             // "2026-05-09 11:00:00"
    val idleScene: IdleScene = IdleScene(),
    val scenes: List<Scene> = emptyList()
) {
    /** Convenience: all missing-asset locations as (sceneIndex?, isIdle, label). */
    fun findMissingAssets(): List<MissingAsset> {
        val result = mutableListOf<MissingAsset>()
        if (idleScene.hasMissingAsset()) {
            result.add(MissingAsset(isIdle = true, sceneIndex = null, label = "idle"))
        }
        scenes.forEachIndexed { i, s ->
            if (s.hasMissingAsset()) {
                result.add(MissingAsset(isIdle = false, sceneIndex = i, label = s.name.ifBlank { "Scene ${i + 1}" }))
            }
        }
        return result
    }
}

data class MissingAsset(
    val isIdle: Boolean,
    val sceneIndex: Int?,
    val label: String
)
