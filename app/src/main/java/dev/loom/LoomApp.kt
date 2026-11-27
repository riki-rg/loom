package dev.loom

import android.app.Application
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
