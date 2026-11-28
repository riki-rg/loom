package dev.loom.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.loom.R
import dev.loom.core.Downloads
import dev.loom.core.Source
import dev.loom.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs playlist downloads in a foreground service.
 *
 * Without this a 50-track download lived in `viewModelScope`: backgrounding the app killed the
 * process mid-playlist and the rest silently never arrived. The notification also gives the only
 * honest progress readout, since the user is usually not looking at the app while it runs.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val raw = intent?.getStringExtra(EXTRA_TRACKS) ?: return stopNow()
        val playlist = intent.getStringExtra(EXTRA_PLAYLIST)
        val tracks = parse(raw)
        if (tracks.isEmpty()) return stopNow()

        startForeground(NOTIF_ID, notification("Menyiapkan unduhan", 0, tracks.size, indeterminate = true))
        job = scope.launch {
            var done = 0
            for (t in tracks) {
                progress.value = Progress("${done + 1}/${tracks.size} ${t.title}", done, tracks.size, 0f)
                notify(notification(t.title, done, tracks.size, indeterminate = false, sub = "${done + 1}/${tracks.size}"))
                runCatching {
                    Downloads.download(applicationContext, t, playlist) { p ->
                        progress.value = Progress(t.title, done, tracks.size, p)
                        notify(notification(t.title, done, tracks.size, indeterminate = false, sub = "${done + 1}/${tracks.size}", fraction = p))
                    }
                }
                done++
            }
            progress.value = Progress(null, done, tracks.size, 1f)
            finished.value = true
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopNow(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun notify(n: Notification) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun notification(
        title: String,
        done: Int,
        total: Int,
        indeterminate: Boolean,
        sub: String = "",
        fraction: Float = 0f,
    ): Notification {
        ensureChannel()
        // One bar per track, so a 50-track playlist visibly advances instead of sitting at 0.
        val pct = if (indeterminate) 0 else ((done + fraction) / total.coerceAtLeast(1) * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (total > 1) "Mengunduh playlist ($done/$total)" else "Mengunduh lagu")
            .setContentText(title + if (sub.isNotEmpty()) " · $sub" else "")
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Unduhan", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "loom_downloads"
        private const val NOTIF_ID = 4242
        private const val EXTRA_TRACKS = "tracks"
        private const val EXTRA_PLAYLIST = "playlist"

        /** Read by the ViewModel so the Download tab and a progress bar can follow along. */
        val progress = MutableStateFlow(Progress(null, 0, 0, 0f))
        val finished = MutableStateFlow(false)

        data class Progress(val title: String?, val done: Int, val total: Int, val fraction: Float)

        /** Kicks off a playlist download. Tracks travel as JSON: small, and no Parcelable plumbing. */
        fun start(ctx: Context, tracks: List<Track>, playlist: String?) {
            if (tracks.isEmpty()) return
            finished.value = false
            val arr = JSONArray()
            tracks.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id).put("title", t.title).put("artist", t.artist)
                        .put("art", t.art).put("source", t.source.name).put("duration", t.durationMs),
                )
            }
            val i = Intent(ctx, DownloadService::class.java)
                .putExtra(EXTRA_TRACKS, arr.toString())
                .putExtra(EXTRA_PLAYLIST, playlist)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }

        private fun parse(raw: String): List<Track> = runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Track(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    art = o.optString("art"),
                    source = runCatching { Source.valueOf(o.optString("source")) }.getOrDefault(Source.YT_MUSIC),
                    durationMs = o.optLong("duration"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
