package dev.loom.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.loom.core.Music
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

    /** The player's listener, kept so a recreated activity can detach it instead of stacking. */
    private var listener: Player.Listener? = null
    private var bound: LoomViewModel? = null

    fun attach(ctx: Context, vm: LoomViewModel) {
        // MainActivity calls this from onCreate, and a configuration change runs onCreate again.
        // Without this guard each recreation added another listener and another controller, so one
        // pause from the system fired twice and one controller could outlive the other.
        if (bound === vm && controller != null) return
        bound = vm
        listener?.let { controller?.removeListener(it) }
        val app = ctx.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            val l = object : Player.Listener {
                // The notification's next/prev (and repeat/shuffle) drive the player directly, so
                // the ViewModel has to follow the item that actually started playing.
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    val uri = item?.localConfiguration?.uri?.toString() ?: return
                    val t = Music.trackOfUri(uri) ?: return
                    if (t.id == vm.state.value.current?.id) return
                    vm.onPlayerMoved(t, reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                }

                // The player is the only truth for whether audio is moving. Anything that pauses
                // it from outside — a WhatsApp voice note taking audio focus, the notification,
                // headphones unplugged — lands here, and the button has to follow.
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    vm.onPlayingChanged(isPlaying)
                }

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
            }
            listener = l
            controller?.addListener(l)
            vm.onPlay = ::play
            vm.onPause = { controller?.pause() }
            vm.onResume = ::resume
            vm.onEq = { name -> Eq.apply(name) }
            queueProvider = { vm.state.value.queue }
            vm.onPlayFile = ::playFile
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
        // The session used to hold a single item, so the notification had no queue to drive:
        // no next, no repeat, no shuffle, and the title only refreshed on the next resolve.
        // With the real queue attached, Media3 exposes all four commands itself.
        val queue = (queueProvider?.invoke() ?: emptyList())
            .map { t ->
                if (t.id == track.id && t.source == track.source) item
                else PlaybackService.mediaItem(t.id, Music.mediaUri(t), t.title, t.artist, t.art)
            }
            .ifEmpty { listOf(item) }
        c.setMediaItems(queue, queue.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0), 0L)
        c.prepare()
        c.play()
    }

    /** A downloaded file needs no resolving: the bytes are already on disk. */
    private fun playFile(d: dev.loom.core.Downloaded) {
        val c = controller ?: return
        ended = false
        val item = PlaybackService.mediaItem(d.uri.toString(), d.uri.toString(), d.title, "Offline", "")
        c.setMediaItems(listOf(item), 0, 0L)
        c.prepare()
        c.play()
    }

    /** Set by the ViewModel: the live queue, so the session can expose a real playlist. */
    var queueProvider: (() -> List<Track>)? = null

    fun release() {
        listener?.let { controller?.removeListener(it) }
        listener = null
        bound = null
        controller?.release()
        controller = null
        Eq.release()
    }
}
