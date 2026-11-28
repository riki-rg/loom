import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing key lives outside git; keystore.properties is gitignored. Without it the build falls
// back to the debug key so a clone still compiles.

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.loom"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "dev.loom"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.1.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric downloads android-all from Maven; point it at the jar we already
                // have so it does not fight the slow link mid-test.
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty(
                    "robolectric.dependency.dir",
                    rootProject.file("robolectric-deps").absolutePath,
                )
                // Robolectric resolves android-all-<sdk> by exact filename in offline mode.
                it.systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
                it.maxHeapSize = "2g"
            }
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // MIUI/HyperOS installers reject v2-only APKs with "problem parsing the
                // package"; v1 costs nothing and covers them.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // A debug key made some installers reject the package outright; sign properly.
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// The .so is not built by AGP. Without this the APK ships whatever jniLibs held last, which is
// how v0.9.0 went out with a core that predated the new JNI symbols and died with
// UnsatisfiedLinkError on first call. Wiring it to the task graph makes that impossible.
val buildCore by tasks.registering(Exec::class) {
    workingDir = rootProject.projectDir
    commandLine("bash", "build-core.sh")
    inputs.dir(rootProject.file("core/src"))
    inputs.file(rootProject.file("core/Cargo.toml"))
    inputs.file(rootProject.file("build-core.sh"))
    outputs.dir(rootProject.file("app/src/main/jniLibs"))
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(buildCore) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    // Offline downloads: Transformer re-encodes a trimmed range into a real file.
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Coil reads this resource at startup: shared OkHttp client, memory+disk cache sized for
    // scrolling shelves, and RGB_565 bitmaps. Covers are opaque, so alpha buys nothing.
    implementation("io.coil-kt:coil:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Real backdrop blur for the glass chrome; its Compose 1.7.x line matches the BOM above.
    implementation("dev.chrisbanes.haze:haze:1.2.0")

    // JVM-only: lets the JSON parsing be checked without a device.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // Real Skia rendering on the JVM: Compose screenshots without an emulator or /dev/kvm.
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

}
