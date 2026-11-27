# JNI entry points are called from Kotlin by name; R8 must keep them and their native peers.
-keepclasseswithmembernames class dev.loom.core.Core { native <methods>; }
-keep class dev.loom.core.Track { *; }
-keep class dev.loom.core.Stream { *; }

# Referenced only from AndroidManifest.xml; R8 drops it and the app crashes on play.
-keep class dev.loom.player.PlaybackService { *; }
