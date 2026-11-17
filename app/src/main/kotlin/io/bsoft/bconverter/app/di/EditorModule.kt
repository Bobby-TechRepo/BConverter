package io.bsoft.bconverter.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.data.storage.AndroidUriPermissionPersister
import io.bsoft.bconverter.domain.repository.UriPermissionPersister
import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.timeline.UuidIdGenerator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EditorModule {
    @Provides
    fun idGenerator(): IdGenerator = UuidIdGenerator

    @Provides
    fun subtitleImporter(
        reader: io.bsoft.bconverter.core.storage.ContentReader,
        ids: IdGenerator,
        @io.bsoft.bconverter.core.coroutine.IoDispatcher dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = io.bsoft.bconverter.domain.usecase.SubtitleImporter(reader, ids, dispatcher)

    @Provides @Singleton
    fun overlayImageImporter(
        @ApplicationContext context: Context,
        @io.bsoft.bconverter.core.coroutine.IoDispatcher dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): io.bsoft.bconverter.domain.overlay.OverlayImageImporter =
        io.bsoft.bconverter.media.overlay.AndroidOverlayImageImporter(context, java.io.File(context.filesDir, "overlay-images"), dispatcher)

    @Provides @Singleton
    fun uriPermissionPersister(@ApplicationContext context: Context): UriPermissionPersister =
        AndroidUriPermissionPersister(context.contentResolver)
}
