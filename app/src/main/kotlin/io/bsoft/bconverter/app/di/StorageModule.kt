package io.bsoft.bconverter.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.core.coroutine.IoDispatcher
import io.bsoft.bconverter.core.storage.AndroidContentReader
import io.bsoft.bconverter.core.storage.ContentReader
import io.bsoft.bconverter.core.storage.FreeSpaceProvider
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.storage.DefaultInputResolver
import io.bsoft.bconverter.data.storage.AndroidOutputPublisher
import io.bsoft.bconverter.domain.repository.InputResolver
import io.bsoft.bconverter.domain.repository.OutputPublisher
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @IoDispatcher fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton
    fun contentReader(@ApplicationContext context: Context): ContentReader =
        AndroidContentReader(context.contentResolver)

    @Provides @Singleton
    fun freeSpaceProvider(): FreeSpaceProvider = FreeSpaceProvider { it.usableSpace }

    @Provides @Singleton
    fun stagingDirectory(@ApplicationContext context: Context) = StagingDirectory(File(context.cacheDir, "staging"))

    @Provides @Singleton
    fun outputPublisher(@ApplicationContext context: Context, @IoDispatcher dispatcher: CoroutineDispatcher): OutputPublisher =
        AndroidOutputPublisher(context.contentResolver, dispatcher)

    @Provides @Singleton
    fun inputResolver(
        reader: ContentReader,
        staging: StagingDirectory,
        freeSpace: FreeSpaceProvider,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ): InputResolver = DefaultInputResolver(reader, staging, freeSpace, dispatcher)
}
