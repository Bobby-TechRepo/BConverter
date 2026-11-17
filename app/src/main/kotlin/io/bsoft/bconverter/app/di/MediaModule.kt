package io.bsoft.bconverter.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import io.bsoft.bconverter.media.ffmpeg.FfmpegFailureLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.core.coroutine.IoDispatcher
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.repository.CapabilitySource
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.media.api.MediaEngine
import io.bsoft.bconverter.media.device.AndroidCodecInfoProvider
import io.bsoft.bconverter.media.device.MediaCodecCapabilitySource
import io.bsoft.bconverter.media.ffmpeg.CapabilityProvider
import io.bsoft.bconverter.media.ffmpeg.DefaultFfmpegCommandBuilder
import io.bsoft.bconverter.media.ffmpeg.FfmpegExecutor
import io.bsoft.bconverter.media.ffmpeg.FfmpegKitExecutor
import io.bsoft.bconverter.media.ffmpeg.FfmpegKitFfprobeRunner
import io.bsoft.bconverter.media.ffmpeg.FfmpegKitListRunner
import io.bsoft.bconverter.media.ffmpeg.FfmpegListRunner
import io.bsoft.bconverter.media.ffmpeg.FfmpegMediaEngine
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunner
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @Provides @Singleton
    fun ffprobeRunner(@IoDispatcher dispatcher: CoroutineDispatcher): FfprobeRunner = FfmpegKitFfprobeRunner(dispatcher)

    @Provides @Singleton
    fun listRunner(@IoDispatcher dispatcher: CoroutineDispatcher): FfmpegListRunner = FfmpegKitListRunner(dispatcher)

    @Provides @Singleton
    fun executor(@ApplicationContext context: Context): FfmpegExecutor =
        FfmpegKitExecutor(FfmpegFailureLog(File(context.filesDir, "diagnostics")))

    @Provides @Singleton
    fun capabilityProvider(runner: FfmpegListRunner): CapabilityProvider =
        CapabilityProvider(runner, MediaCodecCapabilitySource(AndroidCodecInfoProvider()))

    @Provides @Singleton
    fun capabilitySource(provider: CapabilityProvider): CapabilitySource = provider

    @Provides @Singleton
    fun mediaProber(resolver: InputResolver, runner: FfprobeRunner) = MediaProber(resolver, runner)

    @Provides @Singleton
    fun mediaEngine(
        @ApplicationContext context: Context,
        prober: MediaProber,
        resolver: InputResolver,
        executor: FfmpegExecutor,
        capabilities: CapabilityProvider,
    ): MediaEngine = io.bsoft.bconverter.media.RoutingMediaEngine(
        FfmpegMediaEngine(prober, resolver, executor) { DefaultFfmpegCommandBuilder(capabilities.encoderNames()) },
        // Projects the device encoders can render exactly go through Media3 Transformer first (step 19).
        io.bsoft.bconverter.media.transformer.Media3ProjectRenderer(context),
    )
}
