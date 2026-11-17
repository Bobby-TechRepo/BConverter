package io.bsoft.bconverter.data.settings

import android.content.Context
import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKitConfig
import io.bsoft.bconverter.domain.about.AppInfo
import io.bsoft.bconverter.domain.about.AppInfoProvider

/** Version of the app and of the FFmpeg build inside it. Loads the native library, so call it off the main thread. */
class AndroidAppInfoProvider(private val context: Context) : AppInfoProvider {
    override fun load(): AppInfo {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val code = when {
            info == null -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode
            else -> @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        val engine = runCatching { FFmpegKitConfig.getFFmpegVersion() }.getOrNull()
        return AppInfo(info?.versionName ?: "unknown", code, engine)
    }
}
