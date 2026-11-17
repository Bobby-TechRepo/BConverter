package io.bsoft.bconverter.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.core.coroutine.IoDispatcher
import io.bsoft.bconverter.data.font.SystemFontScanner
import io.bsoft.bconverter.data.font.UrlConnectionFontHttp
import io.bsoft.bconverter.data.settings.SharedPreferencesSettingsStore
import io.bsoft.bconverter.domain.font.FontCacheStore
import io.bsoft.bconverter.domain.font.FontDownloader
import io.bsoft.bconverter.domain.font.FontHttp
import io.bsoft.bconverter.domain.font.FontLibrary
import io.bsoft.bconverter.domain.font.GoogleFontsCatalog
import io.bsoft.bconverter.domain.font.RecentFonts
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object FontModule {
    @Provides @Singleton
    fun fontHttp(@IoDispatcher io: CoroutineDispatcher): FontHttp = UrlConnectionFontHttp(io)

    /** Downloaded fonts sit in the same folder FFmpeg reads fonts from (see FfmpegFonts). */
    @Provides @Singleton
    fun fontCache(@ApplicationContext context: Context): FontCacheStore =
        FontCacheStore(File(context.filesDir, "fonts"), File(context.filesDir, "font-index.json"))

    @Provides @Singleton
    fun recentFonts(@ApplicationContext context: Context): RecentFonts =
        RecentFonts(SharedPreferencesSettingsStore(context.getSharedPreferences("fonts", Context.MODE_PRIVATE)))

    @Provides @Singleton
    fun fontLibrary(store: FontCacheStore, recents: RecentFonts): FontLibrary =
        FontLibrary(system = { SystemFontScanner().scan() }, downloaded = store::list, recents = recents)

    @Provides @Singleton
    fun fontCatalog(http: FontHttp, @ApplicationContext context: Context): GoogleFontsCatalog =
        GoogleFontsCatalog(http, File(context.cacheDir, "google-fonts-catalog.json"))

    @Provides @Singleton
    fun fontDownloader(http: FontHttp, store: FontCacheStore): FontDownloader = FontDownloader(http, store)
}
