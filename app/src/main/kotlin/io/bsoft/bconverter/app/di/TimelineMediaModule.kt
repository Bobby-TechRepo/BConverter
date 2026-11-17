package io.bsoft.bconverter.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.data.storage.DiskThumbnailStore
import io.bsoft.bconverter.data.storage.DiskWaveformStore
import io.bsoft.bconverter.domain.media.ThumbnailService
import io.bsoft.bconverter.domain.media.WaveformService
import io.bsoft.bconverter.media.ffmpeg.FfmpegExecutor
import io.bsoft.bconverter.media.ffmpeg.FfmpegWaveformGenerator
import io.bsoft.bconverter.media.thumbnail.MediaMetadataThumbnailGenerator
import java.io.File
import javax.inject.Singleton

/** Thumbnails and waveforms live in the cache directory, so the system may reclaim them; they are always regenerable. */
@Module
@InstallIn(SingletonComponent::class)
object TimelineMediaModule {
    private const val THUMBNAIL_BUDGET_BYTES = 50L * 1024 * 1024

    @Provides @Singleton
    fun thumbnailStore(@ApplicationContext context: Context): DiskThumbnailStore =
        DiskThumbnailStore(File(context.cacheDir, "thumbnails"), THUMBNAIL_BUDGET_BYTES)

    @Provides @Singleton
    fun thumbnailService(store: DiskThumbnailStore): ThumbnailService = ThumbnailService(MediaMetadataThumbnailGenerator(), store)

    @Provides @Singleton
    fun waveformService(@ApplicationContext context: Context, executor: FfmpegExecutor): WaveformService =
        WaveformService(
            FfmpegWaveformGenerator(executor, File(context.cacheDir, "waveform-tmp")),
            DiskWaveformStore(File(context.cacheDir, "waveforms")),
        )
}
