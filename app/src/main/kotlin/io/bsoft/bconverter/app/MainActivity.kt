package io.bsoft.bconverter.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.bsoft.bconverter.core.ui.BConverterTheme
import io.bsoft.bconverter.core.ui.OrientationPolicy
import io.bsoft.bconverter.core.ui.ScreenOrientation
import io.bsoft.bconverter.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation =
            when (OrientationPolicy.forSmallestWidthDp(resources.configuration.smallestScreenWidthDp)) {
                ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        enableEdgeToEdge()
        setContent { BConverterTheme { AppNavHost() } }
    }
}
