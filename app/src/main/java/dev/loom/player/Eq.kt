package dev.loom.player

import android.media.audiofx.Equalizer

/**
 * Presets applied to the live audio session. `android.media.audiofx.Equalizer` is part of the
 * platform, so no dependency is added; band gains are in millibels as the API expects.
 *
 * A plain `Equalizer` is deprecated in favour of `android.media.audiofx.Visualizer`-era
 * dynamics processing, but it is still the only EQ that works without extra permissions and
 * still ships on every device back to API 26. Levels are deliberately gentle: a phone speaker
 * distorts fast, so ±6dB is the useful range.
 */
object Eq {
    /** Band gains in dB, applied to whichever bands the device reports. */
    private val presets: Map<String, FloatArray> = mapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Bass" to floatArrayOf(6f, 4f, 0f, -1f, -2f),
        "Vocal" to floatArrayOf(-2f, 0f, 4f, 5f, 2f),
        "Treble" to floatArrayOf(-2f, -1f, 0f, 4f, 6f),
        "Loud" to floatArrayOf(5f, 3f, 1f, 3f, 5f),
    )

    val names: List<String> get() = presets.keys.toList()

    private var eq: Equalizer? = null
    private var session = 0
    private var current = "Flat"

    /** Rebind to a new audio session id; ExoPlayer hands out a fresh one per player instance. */
    fun bind(sessionId: Int) {
        if (sessionId == session && eq != null) return
        release()
        session = sessionId
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()?.also {
            it.enabled = true
            apply(current)
        }
    }

    fun apply(name: String) {
        current = name
        val e = eq ?: return
        val gains = presets[name] ?: presets.getValue("Flat")
        val count = e.numberOfBands.toInt()
        if (count <= 0) return
        runCatching {
            for (b in 0 until count) {
                // Presets are written for five bands; stretch them across whatever exists.
                val g = gains[(b.toFloat() / count * gains.size).toInt().coerceIn(0, gains.size - 1)]
                val level = (g * 100).toInt().toShort() // dB -> millibels
                val range = e.bandLevelRange
                e.setBandLevel(b.toShort(), level.coerceIn(range[0], range[1]))
            }
        }
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
    }
}
