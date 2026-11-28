package dev.loom

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * One image loader for the whole app. Covers are opaque album art, so RGB_565 halves the
 * memory each bitmap takes with no visible loss, and the caches are sized for scrolling
 * shelves rather than Coil's small defaults.
 */
class LoomApp : Application(), ImageLoaderFactory {
    /**
     * Whether an Activity is on screen. The island is for *outside* the app, so the service hides
     * it while Loom is in front — otherwise two now-playing surfaces show at once.
     */
    object Foreground {
        @Volatile var visible = false
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                Foreground.visible = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                Foreground.visible = started > 0
                // No player event fires on backgrounding, so the island would only appear at the
                // next track change. Ask the service to resync now.
                sendBroadcast(
                    android.content.Intent(dev.loom.player.PlaybackService.ACTION_REFRESH)
                        .setPackage(packageName)
                )
            }

            override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .crossfade(120)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(96L * 1024 * 1024)
                .build()
        }
        .build()
}
