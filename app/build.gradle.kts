import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin support is built into AGP 9; the kotlin-android plugin must not be applied.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing: a git-ignored keystore.properties in the project root (storeFile, storePassword, keyAlias, keyPassword),
// or the same four values as BCONVERTER_* environment variables on CI. Nothing set = release builds stay unsigned.
val releaseSigning: Map<String, String?> = run {
    val props = Properties()
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { props.load(it) }
    fun value(key: String, env: String) = props.getProperty(key) ?: System.getenv(env)
    mapOf(
        "storeFile" to value("storeFile", "BCONVERTER_STORE_FILE"),
        "storePassword" to value("storePassword", "BCONVERTER_STORE_PASSWORD"),
        "keyAlias" to value("keyAlias", "BCONVERTER_KEY_ALIAS"),
        "keyPassword" to value("keyPassword", "BCONVERTER_KEY_PASSWORD"),
    )
}
val hasReleaseSigning = releaseSigning.values.all { !it.isNullOrBlank() }

android {
    namespace = "io.bsoft.bconverter"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.bsoft.bconverter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The FFmpegKit fork publishes arm64-v8a only (see docs/FFMPEG.md).
        ndk { abiFilters += "arm64-v8a" }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getValue("storeFile")!!)
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                // Local smoke testing only: ./gradlew assembleRelease -PreleaseWithDebugKey installs without a real keystore.
                project.hasProperty("releaseWithDebugKey") -> signingConfigs.getByName("debug")
                hasReleaseSigning -> signingConfigs.getByName("release")
                else -> null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's SDK 36 sandbox (the app now targets API 36) reaches into java.io.FileDescriptor through
        // jdk.internal.access.SharedSecrets, which java.base does not export by default; without this every Robolectric test
        // fails in setup with "Failed to interact with raw FileDescriptor internals". JDK 21 is required for SDK 36 too.
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
        // Opt in with -Porchestrator=true: every device test then runs in its own process, so a native crash fails
        // only that test (and is named in the report) instead of ending the whole run.
        if (project.hasProperty("orchestrator")) execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    // The critical-flow UI tests drive the real ViewModels with the same fakes and ffprobe samples as the JVM tests.
    // With AGP 9's built-in Kotlin, Kotlin source directories are added through the Android source set (not kotlin.sourceSets).
    sourceSets.named("androidTest") {
        resources.directories += "src/test/resources"
        // Room's MigrationTestHelper reads the exported schemas from the test APK's assets.
        assets.directories += "$projectDir/schemas"
        kotlin.directories += "src/test/kotlin/io/bsoft/bconverter/fakes"
    }
}

// Room writes one JSON schema per database version here; commit them so migrations can be generated and tested.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    // Uses the JDK that runs Gradle (17 or newer) instead of auto-downloading one; Gradle 10 will reject auto-provisioning.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ffmpeg.kit.full)
    implementation(libs.smart.exception.java)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    if (project.hasProperty("orchestrator")) androidTestUtil("androidx.test:orchestrator:1.6.1")
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Windows: the Android test runner keeps a lock file (utp.0.log.lck) open in the results directory, which Gradle
// cannot hash when it fingerprints the task's outputs. Device tests are never up-to-date anyway, so skip tracking.
tasks.configureEach {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        doNotTrackState("Instrumented test results contain lock files that Gradle cannot hash on Windows")
    }
}
