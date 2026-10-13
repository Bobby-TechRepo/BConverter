package io.bsoft.bconverter.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.core.storage.FreeSpaceProvider
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.repository.ExportScheduler
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.OutputPublisher
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
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

    @Provides @Singleton
    fun exportRegistry(): ExportRegistry = ExportRegistry()

    @Provides @Singleton
    fun exportExecutor(engine: MediaEngine, resolver: InputResolver, publisher: OutputPublisher, registry: ExportRegistry): ExportJobExecutor =
        ExportJobExecutor(engine, resolver, publisher, registry)

    @Provides @Singleton
    fun exportScheduler(@ApplicationContext context: Context): ExportScheduler = WorkManagerExportScheduler(context)

    @Provides @Singleton
    fun exportNotifier(@ApplicationContext context: Context): ExportNotifier = AndroidExportNotifier(context)

    @Provides @Singleton
    fun exportLauncher(registry: ExportRegistry, scheduler: ExportScheduler): ExportJobLauncher = ExportJobLauncher(registry, scheduler)
}
