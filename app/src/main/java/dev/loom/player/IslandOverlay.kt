package dev.loom.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.loom.MainActivity
import dev.loom.core.Track
import dev.loom.ui.IslandSpec
import kotlin.math.sin

/**
 * The floating capsule over other apps. One window, added and removed by [PlaybackService].
 *
 * Plain views on purpose: a `TYPE_APPLICATION_OVERLAY` window has no Activity, so Compose would
 * need a lifecycle owner and saved-state registry just to draw one row.
 *
 * Lock screen: the window carries `FLAG_SHOW_WHEN_LOCKED`, which OEMs honour inconsistently. The
 * guaranteed lock-screen surface stays the media notification, which Android renders there itself.
 * `shortcut:` best-effort lock screen, replace with a media notification style when it must be exact.
 */
class IslandOverlay(
    private val ctx: Context,
    /** Live (position, duration) in ms. Read on every tick, so the clock and ring actually move. */
    private val live: () -> Pair<Long, Long> = { 0L to 0L },
) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    internal var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var disc: DiscView? = null
    private var title: TextView? = null
    private var time: TextView? = null
    private var bars: BarsView? = null
    private var playPause: ImageView? = null

    private var track: Track? = null
    private var playing = false
    private var positionMs = 0L
    private var durationMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            live().let { (pos, dur) -> positionMs = pos; durationMs = dur }
            push()
            if (playing) Handler(Looper.getMainLooper()).postDelayed(this, 250)
        }
    }

    /** True when the window is on screen, so the service does not add it twice. */
    val attached: Boolean get() = root != null

    fun show(t: Track, isPlaying: Boolean, pos: Long, dur: Long) {
        track = t; playing = isPlaying; positionMs = pos; durationMs = dur
        if (!Settings.canDrawOverlays(ctx)) return
        // Rebuilding an attached window would stack a second one on top and leak the first.
        if (root == null) build()
        push()
        val h = Handler(Looper.getMainLooper())
        h.removeCallbacks(tick)
        if (isPlaying) h.postDelayed(tick, 250)
    }

    fun update(isPlaying: Boolean, pos: Long, dur: Long) {
        show(track ?: return, isPlaying, pos, dur)
    }

    /** Detaches the view but keeps it, so a test can measure and draw it. */
    internal fun detachForTest() {
        Handler(Looper.getMainLooper()).removeCallbacks(tick)
        root?.let { runCatching { wm.removeView(it) } }
    }

    fun hide() {
        Handler(Looper.getMainLooper()).removeCallbacks(tick)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        params = null
        // Without this the queued tick kept writing into detached views.
        track = null
    }

    private fun push() {
        val t = track ?: return
        title?.text = t.title
        time?.text = "${IslandSpec.clock(positionMs)} · ${IslandSpec.clock(durationMs)}"
        val icon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        if (playPause?.tag != icon) {
            playPause?.setImageResource(icon)
            playPause?.tag = icon
        }
        disc?.apply {
            spinning = playing
            progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            invalidate()
        }
        bars?.apply { active = playing; phase = positionMs / 1000f; invalidate() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build() {
        val capsule = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(IslandSpec.RADIUS_DP).toFloat()
                setColor(Color.argb(242, 0, 0, 0))
            }
            setPadding(dp(IslandSpec.PAD_DP), 0, dp(IslandSpec.PAD_DP), 0)
            // Tapping the body opens the app on the lyrics page, not the whole player.
            setOnClickListener {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(ctx, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_OPEN_LYRICS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }
            }
        }

        val discView = DiscView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(IslandSpec.ART_DP), dp(IslandSpec.ART_DP))
        }
        capsule.addView(discView)
        capsule.addView(spacer(dp(8)))

        val text = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0)
        }
        val timeView = TextView(ctx).apply {
            setTextColor(Color.argb(170, 255, 255, 255))
            textSize = 10f
            maxLines = 1
        }
        text.addView(titleView)
        text.addView(timeView)
        capsule.addView(text)
        capsule.addView(spacer(dp(6)))

        val barsView = BarsView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(12))
        }
        capsule.addView(barsView)
        capsule.addView(spacer(dp(6)))

        capsule.addView(button(android.R.drawable.ic_media_previous) { send(PlaybackService.ACTION_PREV) })
        val pp = button(android.R.drawable.ic_media_pause) { send(PlaybackService.ACTION_TOGGLE) }
        capsule.addView(pp)
        capsule.addView(button(android.R.drawable.ic_media_next) { send(PlaybackService.ACTION_NEXT) })

        disc = discView
        title = titleView
        time = timeView
        bars = barsView
        playPause = pp
        // Fill the text first: an empty TextView measures to almost nothing.
        push()

        // Cap relative to this screen: a fixed cap wider than a small phone ran off both edges.
        val screenDp = ctx.resources.configuration.screenWidthDp
        val capDp = IslandSpec.maxWidthDp(screenDp)
        capsule.measure(
            View.MeasureSpec.makeMeasureSpec(dp(capDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(IslandSpec.HEIGHT_DP), View.MeasureSpec.EXACTLY),
        )

        val lp = WindowManager.LayoutParams(
            dp(capDp),
            dp(IslandSpec.HEIGHT_DP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Status bar height, so the capsule clears the clock and the notch cutout.
            y = dp(IslandSpec.TOP_DP) + statusBarHeight()
        }

        runCatching { wm.addView(capsule, lp) }.onFailure { return }
        root = capsule
        params = lp
    }

    private fun statusBarHeight(): Int {
        val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun spacer(w: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(w, 1)
    }

    private fun button(icon: Int, onClick: () -> Unit) = ImageView(ctx).apply {
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        scaleType = ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(4); marginEnd = dp(4) }
        setOnClickListener { onClick() }
    }

    private fun send(action: String) {
        // A broadcast, not startService: the app is in the background while the island is up, and
        // startForegroundService would demand a startForeground() within 5s or crash.
        ctx.sendBroadcast(
            android.content.Intent(action).setPackage(ctx.packageName)
        )
    }

    /** Vinyl: grooves, label, and a progress ring on the rim so the island needs no bar. */
    private class DiscView(ctx: Context) : View(ctx) {
        var progress = 0f
        var spinning = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val r = rect.apply { set(0f, 0f, width.toFloat(), height.toFloat()) }
            val c = width / 2f
            val radius = c - stroke()
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(28, 28, 32)
            canvas.drawCircle(c, c, radius, paint)
            paint.color = Color.rgb(16, 16, 19)
            canvas.drawCircle(c, c, radius * 0.68f, paint)
            paint.color = Color.rgb(70, 120, 220)
            canvas.drawCircle(c, c, radius * 0.30f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(c, c, radius * 0.08f, paint)

            // Track ring, then the played arc.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke()
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.argb(60, 255, 255, 255)
            val inset = RectF(r)
            inset.inset(stroke() / 2, stroke() / 2)
            canvas.drawArc(inset, -90f, 360f, false, paint)
            if (progress > 0f) {
                paint.color = Color.WHITE
                canvas.drawArc(inset, -90f, 360f * progress, false, paint)
            }
        }

        private fun stroke() = resources.displayMetrics.density * 2.2f
    }

    /** Four bars, a deterministic function of position. Not an FFT, same as the in-app meter. */
    private class BarsView(ctx: Context) : View(ctx) {
        var active = false
        var phase = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        override fun onDraw(canvas: Canvas) {
            val n = 4
            val gap = width * 0.14f
            val bw = (width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val h = if (active) {
                    (0.25f + 0.75f * ((sin(phase * 3.1f + i * 1.7f) + 1f) / 2f)) * height
                } else {
                    height * 0.22f
                }
                val left = i * (bw + gap)
                val top = height - h
                canvas.drawRoundRect(left, top, left + bw, height.toFloat(), bw / 2, bw / 2, paint)
            }
        }
    }
}
