package dev.loom.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.loom.core.Stream
import dev.loom.core.Track
import dev.loom.ui.LoomViewModel

/**
 * Wires the ViewModel's play/pause callbacks to the MediaSessionService.
 * One controller for the app lifetime; the service owns the actual ExoPlayer.
 */
object Playback {
    private var controller: MediaController? = null
    private var ended = false

    /** Polled by the player sheet; Media3 has no push for position. */
    fun position(): Pair<Long, Long> {
        val c = controller ?: return 0L to 0L
        return c.currentPosition.coerceAtLeast(0) to c.duration.coerceAtLeast(0)
    }

    fun seek(ms: Long) { controller?.seekTo(ms) }

    /** Relative seek for the -10s / +10s buttons; clamped to the item so it cannot wrap. */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceIn(0L, c.duration.coerceAtLeast(0L))
        c.seekTo(target)
    }

    /**
     * Repeat-one and shuffle belong to the player, not to the ViewModel's end-of-track handler.
     * Looping here means ExoPlayer never reports STATE_ENDED, so a repeating track cannot stall
     * on a finished player and then jump when play is pressed again.
     */
    fun setRepeat(on: Boolean) {
        controller?.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun setShuffle(on: Boolean) {
        controller?.shuffleModeEnabled = on
    }

    fun attach(ctx: Context, vm: LoomViewModel) {
        val app = ctx.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    // Guard against a repeat callback for the same finished item.
                    if (state == Player.STATE_ENDED) {
                        if (!ended) {
                            ended = true
                            vm.onTrackEnded()
                        }
                    } else {
                        ended = false
                    }
                }
            })
            vm.onPlay = ::play
            vm.onPause = { controller?.pause() }
            vm.onResume = ::resume
            vm.onEq = { name -> Eq.apply(name) }
            vm.onRepeat = { Playback.setRepeat(it) }
            vm.onShuffle = { Playback.setShuffle(it) }
            // Apply whatever the user had set before this controller was ready.
            vm.reapplyModes()
        }, ContextCompat.getMainExecutor(app))
    }

    /**
     * Play, or restart when the item already finished. A player sitting at STATE_ENDED ignores
     * `play()`, which is why pressing play on a track that had ended appeared to do nothing and
     * then jumped forward instead of replaying.
     */
    private fun resume() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_ENDED) {
            c.seekTo(0)
            c.prepare()
        }
        c.play()
    }

    private fun play(track: Track, stream: Stream) {
        val c = controller ?: return
        ended = false
        val item: MediaItem = PlaybackService.mediaItem(
            id = track.id,
            url = stream.url,
            title = track.title,
            artist = track.artist,
            art = track.art,
        )
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    fun release() {
        controller?.release()
        controller = null
        Eq.release()
    }
}
