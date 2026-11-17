package io.bsoft.bconverter

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Tests that run real FFmpeg encodes. The FFmpegKit build is arm64-only, so on an emulator these run under CPU translation
 * (or a very slow arm64 image) and can starve the host until the IDE freezes. They are skipped on emulators by default and
 * run on real devices.
 *
 * Override with an instrumentation argument: `-Pandroid.testInstrumentationRunnerArguments.heavyMedia=true` forces them on
 * (e.g. an arm64 Mac running an arm64 emulator), `=false` forces them off.
 */
class HeavyMediaRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val forced = InstrumentationRegistry.getArguments().getString(ARGUMENT)
            val enabled = when (forced) { "true" -> true; "false" -> false; else -> !isEmulator() }
            if (!enabled) throw AssumptionViolatedException("Heavy FFmpeg test skipped on an emulator; pass $ARGUMENT=true to run it")
            base.evaluate()
        }
    }

    private fun isEmulator(): Boolean =
        Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.startsWith("generic") ||
            Build.PRODUCT.contains("sdk", ignoreCase = true) || Build.PRODUCT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) || Build.MODEL.contains("Android SDK built for")

    private companion object { const val ARGUMENT = "heavyMedia" }
}
