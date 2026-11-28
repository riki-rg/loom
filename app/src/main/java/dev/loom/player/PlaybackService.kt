package dev.loom.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.loom.R
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.loom.core.Music
import kotlinx.coroutines.runBlocking

/** Chrome UA: googlevideo and SoundCloud CDN both reject the ExoPlayer default. */
private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val SEEK_BACK = "dev.loom.SEEK_BACK"
private const val SEEK_FORWARD = "dev.loom.SEEK_FORWARD"

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var island: IslandOverlay? = null

    /**
     * Mirrors the player into the floating island. One listener, so the island can never drift
     * from what is actually playing; it is only created while the overlay permission is granted.
     */
    private val islandListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncIsland()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncIsland()
        override fun onPlaybackStateChanged(playbackState: Int) = syncIsland()
    }

    private fun syncIsland() {
        val p = session?.player ?: return
        // The permission can be granted after the service started, and the switch lives in
        // Settings, so both are checked here rather than once in onCreate.
        // Also hidden while Loom is in front: the in-app pill already covers now-playing there.
        if (!dev.loom.core.Store(this).islandOn ||
            !android.provider.Settings.canDrawOverlays(this) ||
            dev.loom.LoomApp.Foreground.visible
        ) {
            island?.hide()
            return
        }
        val overlay = island ?: IslandOverlay(this) {
            (session?.player?.currentPosition ?: 0L).coerceAtLeast(0L) to
                (session?.player?.duration ?: 0L).coerceAtLeast(0L)
        }.also { island = it }
        val item = p.currentMediaItem
        if (item == null) {
            overlay.hide()
            return
        }
        val meta = item.mediaMetadata
        // The source lives in the item's loom:// URI, not in the media id; guessing YT_MUSIC
        // mislabelled every Spotify and SoundCloud track.
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        val track = dev.loom.core.Track(
            id = item.mediaId,
            source = dev.loom.core.Music.trackOfUri(uri)?.source ?: dev.loom.core.Source.YT_MUSIC,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            art = meta.artworkUri?.toString().orEmpty(),
        )
        overlay.show(
            track,
            p.isPlaying,
            p.currentPosition.coerceAtLeast(0L),
            p.duration.coerceAtLeast(0L),
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Tapping the notification body (not the buttons, which keep their own commands) must land
        // in the app on the current track, not on whatever tab was last open.
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            android.content.Intent(this, dev.loom.MainActivity::class.java)
                .setAction(dev.loom.MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, buildPlayer(this))
            .setSessionActivity(open)
            // The notification's -10s/+10s buttons are custom commands, because the standard
            // media layout only offers prev/play/next. Sent as a relative seek in milliseconds.
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val buttons = listOf(
                        CommandButton.Builder()
                            .setDisplayName("Mundur 10 detik")
                            .setIconResId(R.drawable.ic_replay_10)
                            .setSessionCommand(SessionCommand(SEEK_BACK, Bundle.EMPTY))
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName("Maju 10 detik")
                            .setIconResId(R.drawable.ic_forward_10)
                            .setSessionCommand(SessionCommand(SEEK_FORWARD, Bundle.EMPTY))
                            .build(),
                    )
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .addSessionCommands(
                            listOf(
                                SessionCommand(SEEK_BACK, Bundle.EMPTY),
                                SessionCommand(SEEK_FORWARD, Bundle.EMPTY),
                            ),
                        )
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .setMediaButtonPreferences(buttons)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    val p = session.player
                    val delta = when (customCommand.customAction) {
                        SEEK_BACK -> -10_000L
                        SEEK_FORWARD -> 10_000L
                        else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    }
                    val target = (p.currentPosition + delta).coerceIn(0L, p.duration.coerceAtLeast(0L))
                    p.seekTo(target)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        session?.player?.addListener(islandListener)
        // RECEIVER_NOT_EXPORTED only exists from API 33; on 26-32 the two-arg overload keeps the
        // receiver private to this app anyway, since these actions are never broadcast publicly.
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_TOGGLE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_REFRESH)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(islandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(islandReceiver, filter)
        }
        // The EQ needs a live audio session id, which only ExoPlayer itself exposes.
        session?.player?.let { p ->
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) (p as? ExoPlayer)?.audioSessionId?.takeIf { it != 0 }?.let(Eq::bind)
                }
            })
        }
    }

    /**
     * Buttons on the floating island arrive here. A local broadcast rather than startService: the
     * app is in the background while the island is visible, and startForegroundService would
     * demand a startForeground() within five seconds or kill the process.
     */
    private val islandReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            val p = session?.player ?: return
            when (intent.action) {
                ACTION_TOGGLE -> if (p.isPlaying) p.pause() else p.play()
                ACTION_NEXT -> p.seekToNextMediaItem()
                ACTION_PREV -> p.seekToPreviousMediaItem()
                // Loom left the foreground: no player event fires, so the island has to be asked.
                ACTION_REFRESH -> syncIsland()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        runCatching { unregisterReceiver(islandReceiver) }
        island?.hide()
        island = null
        session?.player?.removeListener(islandListener)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "dev.loom.TOGGLE"
        const val ACTION_NEXT = "dev.loom.NEXT"
        const val ACTION_PREV = "dev.loom.PREV"
        const val ACTION_REFRESH = "dev.loom.REFRESH"

        @OptIn(UnstableApi::class)
        fun buildPlayer(ctx: Context): ExoPlayer {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20_000)
                .setReadTimeoutMs(20_000)
            // Queue items carry no stream URL: resolving 50 tracks up front would be 50 network
            // calls and a stale URL by the time the user reaches track 40. The resolver fills in
            // the URL at the moment a track is actually opened, keyed by the item's mediaId.
            val lazy = ResolvingDataSource.Factory(DefaultDataSource.Factory(ctx, http)) { spec ->
                val track = Music.trackOfUri(spec.uri.toString()) ?: return@Factory spec
                // resolveDataSpec is synchronous and runs on the loading thread, so blocking here
                // is correct: it is the network fetch for this track and nothing else.
                val stream = runBlocking { runCatching { Music.bestStream(track) }.getOrNull() }
                    ?: throw java.io.IOException("cannot resolve ${spec.uri}")
                spec.withUri(android.net.Uri.parse(stream.url))
            }
            return ExoPlayer.Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(lazy))
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        }

        fun mediaItem(id: String, url: String, title: String, artist: String, art: String) =
            MediaItem.Builder()
                .setUri(url)
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setArtworkUri(art.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                        .build(),
                )
                .build()
    }
}
