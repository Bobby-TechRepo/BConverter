package io.bsoft.bconverter.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.core.content.IntentCompat
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.core.ui.OrientationPolicy
import io.bsoft.bconverter.core.ui.ScreenOrientation
import io.bsoft.bconverter.domain.settings.SettingsRepository
import io.bsoft.bconverter.domain.settings.ThemePolicy
import io.bsoft.bconverter.core.ui.ProvideWindowLayout
import io.bsoft.bconverter.navigation.AppNavHost
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var shareInbox: io.bsoft.bconverter.feature.share.ShareInbox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones are locked to portrait; large screens follow the device (Android 16 ignores locks there for API 36 apps).
        requestedOrientation =
            when (OrientationPolicy.forSmallestWidthDp(resources.configuration.smallestScreenWidthDp)) {
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ScreenOrientation.FOLLOW_DEVICE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        enableEdgeToEdge()
        // Step 25: videos shared from another app. Only a fresh start reads the intent; a recreated activity already did.
        if (savedInstanceState == null) receiveShare(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()
            val dark = ThemePolicy.useDark(settings.themeMode, isSystemInDarkTheme())
            // System bar icons follow the app's theme, not only the system's: with the app forced light on a dark system
            // (or the reverse) the default would draw light icons on a light bar.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            BConverterTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) { ProvideWindowLayout { AppNavHost() } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveShare(intent)
    }

    /** Passes what another app shared ("Send" or "Send multiple") to the share screen; anything else is ignored. */
    private fun receiveShare(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val single = if (action == Intent.ACTION_SEND) IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null
        val multiple = if (action == Intent.ACTION_SEND_MULTIPLE) IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) else null
        val clip = intent.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } }
        shareInbox.deliver(io.bsoft.bconverter.domain.share.ShareIntents.items(action, single, multiple, clip))
    }

    private companion object {
        // The defaults androidx.activity uses for three-button navigation.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
