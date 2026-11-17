package io.bsoft.bconverter.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.core.coroutine.ApplicationScope
import io.bsoft.bconverter.core.storage.FreeSpaceProvider
import io.bsoft.bconverter.data.repository.RoomJobHistory
import io.bsoft.bconverter.domain.usecase.JobHistory
import kotlinx.coroutines.CoroutineScope
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.repository.ExportScheduler
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.OutputPublisher
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.ProjectExporter
import io.bsoft.bconverter.domain.usecase.StorageGuard
import io.bsoft.bconverter.feature.export.AndroidExportNotifier
import io.bsoft.bconverter.feature.export.ExportNotifier
import io.bsoft.bconverter.feature.export.WorkManagerExportScheduler
import io.bsoft.bconverter.media.api.MediaEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExportModule {
    @Provides @Singleton
    fun storageGuard(freeSpace: FreeSpaceProvider, staging: StagingDirectory): StorageGuard = StorageGuard(freeSpace, staging)

    /** Durable job history (step 17); also listens to the registry so every job is recorded as it starts and ends. */
    @Provides @Singleton
    fun roomJobHistory(dao: io.bsoft.bconverter.data.database.ExportJobDao, @ApplicationScope scope: CoroutineScope): RoomJobHistory =
        RoomJobHistory(dao, scope)

    @Provides fun jobHistory(history: RoomJobHistory): JobHistory = history

    @Provides @Singleton
    fun exportRegistry(history: RoomJobHistory): ExportRegistry = ExportRegistry(history)

    @Provides @Singleton
    fun exportExecutor(
        engine: MediaEngine, resolver: InputResolver, publisher: OutputPublisher, registry: ExportRegistry, overlays: io.bsoft.bconverter.domain.overlay.OverlayLayerPreparer,
    ): ExportJobExecutor = ExportJobExecutor(engine, resolver, publisher, registry, overlays)

    /** Pictures of drawn text overlays are kept (up to 50 MB) so re-exporting an unchanged overlay does not draw it again. */
    @Provides @Singleton
    fun overlayLayers(@ApplicationContext context: Context, fonts: io.bsoft.bconverter.domain.font.FontLibrary): io.bsoft.bconverter.domain.overlay.OverlayLayerPreparer =
        io.bsoft.bconverter.domain.overlay.CachedOverlayLayerPreparer(
            io.bsoft.bconverter.domain.overlay.OverlayLayerCache(java.io.File(context.cacheDir, "text-layers"), io.bsoft.bconverter.media.overlay.AndroidOverlayRasterizer()), fonts,
        )

    @Provides @Singleton
    fun exportScheduler(@ApplicationContext context: Context): ExportScheduler = WorkManagerExportScheduler(context)

    @Provides @Singleton
    fun exportNotifier(@ApplicationContext context: Context): ExportNotifier = AndroidExportNotifier(context)

    @Provides @Singleton
    fun projectExporter(staging: StagingDirectory, storage: StorageGuard, launcher: ExportJobLauncher): ProjectExporter =
        ProjectExporter(staging, storage, launcher, fontsDir = { io.bsoft.bconverter.media.ffmpeg.FfmpegFonts.fontsDir })

    /** Step 25: converts several files one after another; app-wide, so a batch goes on when its screen is left. */
    @Provides @Singleton
    fun batchConverter(
        prober: io.bsoft.bconverter.data.media.MediaProber,
        capabilities: io.bsoft.bconverter.domain.repository.CapabilitySource,
        resolver: InputResolver,
        staging: StagingDirectory,
        storage: StorageGuard,
        launcher: ExportJobLauncher,
        documents: io.bsoft.bconverter.domain.repository.DocumentCreator,
        @ApplicationScope scope: CoroutineScope,
    ): io.bsoft.bconverter.domain.usecase.BatchConverter =
        io.bsoft.bconverter.domain.usecase.BatchConverter(prober, capabilities, resolver, staging, storage, launcher, documents, scope)

    @Provides @Singleton
    fun exportLauncher(registry: ExportRegistry, scheduler: ExportScheduler): ExportJobLauncher = ExportJobLauncher(registry, scheduler)
}
