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

    @Provides @Singleton
    fun uriPermissionPersister(@ApplicationContext context: Context): UriPermissionPersister =
        AndroidUriPermissionPersister(context.contentResolver)
}
