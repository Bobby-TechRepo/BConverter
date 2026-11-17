package io.bsoft.bconverter.app.di

import io.bsoft.bconverter.domain.settings.LanguageController
import io.bsoft.bconverter.data.settings.AppCompatLanguageController
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.data.settings.AndroidAppInfoProvider
import io.bsoft.bconverter.data.settings.SharedPreferencesSettingsStore
import io.bsoft.bconverter.domain.about.AppInfoProvider
import io.bsoft.bconverter.domain.settings.CacheManager
import io.bsoft.bconverter.domain.settings.CacheStorage
import io.bsoft.bconverter.domain.settings.PreferencesSettingsRepository
import io.bsoft.bconverter.domain.settings.SettingsRepository
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        PreferencesSettingsRepository(SharedPreferencesSettingsStore(context.getSharedPreferences("settings", Context.MODE_PRIVATE)))

    /** Only regenerable previews: never the staged copies of imported media that open projects still use. */
    @Provides @Singleton
    fun cacheStorage(@ApplicationContext context: Context): CacheStorage =
        CacheManager(listOf("thumbnails", "waveforms", "waveform-tmp", "text-layers").map { File(context.cacheDir, it) })

    @Provides @Singleton
    fun languageController(): LanguageController = AppCompatLanguageController()

    @Provides @Singleton
    fun appInfoProvider(@ApplicationContext context: Context): AppInfoProvider = AndroidAppInfoProvider(context)
}
