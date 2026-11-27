package dev.loom.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A theme the user can pick: one accent seed drives the whole palette. */
enum class Palette(val label: String, val seed: Long) {
    COBALT("Cobalt", 0xFF2563EB),
    INDIGO("Indigo", 0xFF6C63FF),
    MINT("Mint", 0xFF22C55E),
    SUNSET("Sunset", 0xFFFF6B57),
    OCEAN("Ocean", 0xFF2E9BFF),
    MAGENTA("Magenta", 0xFFE040A0),
    AMBER("Amber", 0xFFFFB020),
    MONO("Mono", 0xFF9AA0A6);

    fun color() = Color(seed)
}

enum class Mode { SYSTEM, LIGHT, DARK, AMOLED }

data class ThemeState(
    val palette: Palette = Palette.COBALT,
    val mode: Mode = Mode.LIGHT,
    /** User-picked accent overrides the palette seed when set. */
    val customSeed: Long? = null,
    val dynamicFromArtwork: Boolean = true,
)

val LocalThemeState = staticCompositionLocalOf { ThemeState() }

private fun seedColor(s: ThemeState) = Color(s.customSeed ?: s.palette.seed)

/**
 * Builds an M3 scheme from one seed instead of shipping 12 hand-written palettes.
 * Tones follow M3: primary at tone 40 (light) / 80 (dark), surfaces at 98/6.
 */
private fun schemeFrom(seed: Color, dark: Boolean, amoled: Boolean) = run {
    val h = seed.toHsl()
    fun tone(t: Float, sat: Float = h.s) = hsl(h.h, sat.coerceIn(0.05f, 1f), t)
    val surface = if (amoled) Color(0xFF000000) else tone(if (dark) 0.06f else 0.985f, 0.28f)
    val onSurface = tone(if (dark) 0.92f else 0.12f, 0.10f)
    val container = if (amoled) Color(0xFF121212) else tone(if (dark) 0.13f else 0.94f, 0.32f)

    val base = if (dark) darkColorScheme() else lightColorScheme()
    base.copy(
        primary = tone(if (dark) 0.80f else 0.42f),
        onPrimary = tone(if (dark) 0.18f else 0.99f, 0.15f),
        primaryContainer = tone(if (dark) 0.30f else 0.90f),
        onPrimaryContainer = tone(if (dark) 0.90f else 0.15f),
        secondary = tone(if (dark) 0.72f else 0.50f, h.s * 0.55f),
        secondaryContainer = tone(if (dark) 0.24f else 0.90f, h.s * 0.5f),
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = container,
        onSurfaceVariant = tone(if (dark) 0.78f else 0.35f, 0.08f),
        surfaceContainer = container,
        surfaceContainerHigh = if (amoled) Color(0xFF1A1A1A) else tone(if (dark) 0.17f else 0.92f, 0.30f),
        outline = tone(if (dark) 0.55f else 0.55f, 0.10f),
    )
}

/** HSL round-trip so a seed produces a coherent palette rather than one tinted grey. */
private fun Color.toHsl(): Hsl {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    if (d == 0f) return Hsl(0f, 0f, l)
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f)
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } / 6f
    return Hsl(h, s, l)
}

private data class Hsl(val h: Float, val s: Float, val l: Float)

private fun hsl(h: Float, s: Float, l: Float): Color {
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    fun hue(t: Float): Float {
        val tt = ((t % 1f) + 1f) % 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6 * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6
            else -> p
        }
    }
    return Color(hue(h + 1f / 3f), hue(h), hue(h - 1f / 3f))
}

private val LoomType = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.4).sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontSize = 19.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 17.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
fun LoomTheme(state: ThemeState, content: @Composable () -> Unit) {
    val dark = when (state.mode) {
        Mode.SYSTEM -> isSystemInDarkTheme()
        Mode.LIGHT -> false
        Mode.DARK, Mode.AMOLED -> true
    }
    val scheme = schemeFrom(seedColor(state), dark, state.mode == Mode.AMOLED)
    androidx.compose.runtime.CompositionLocalProvider(LocalThemeState provides state) {
        MaterialTheme(colorScheme = scheme, typography = LoomType, content = content)
    }
}
