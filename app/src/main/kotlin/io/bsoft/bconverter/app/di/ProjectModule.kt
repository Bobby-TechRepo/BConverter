package io.bsoft.bconverter.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.bsoft.bconverter.data.database.AppDatabase
import io.bsoft.bconverter.data.database.ProjectDao
import io.bsoft.bconverter.data.repository.RoomProjectRepository
import io.bsoft.bconverter.data.storage.AndroidSourceAvailability
import io.bsoft.bconverter.domain.project.ProjectManager
import io.bsoft.bconverter.domain.project.ProjectOpener
import io.bsoft.bconverter.domain.timeline.IdGenerator
import io.bsoft.bconverter.domain.project.ProjectRepository
import io.bsoft.bconverter.domain.project.SourceAvailability
import io.bsoft.bconverter.domain.project.TimeSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProjectModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bconverter.db")
            .addMigrations(io.bsoft.bconverter.data.database.MIGRATION_1_2)
            .build()

    @Provides fun projectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides fun exportJobDao(db: AppDatabase): io.bsoft.bconverter.data.database.ExportJobDao = db.exportJobDao()

    @Provides @Singleton
    fun projectRepository(dao: ProjectDao): ProjectRepository = RoomProjectRepository(dao)

    @Provides @Singleton
    fun sourceAvailability(@ApplicationContext context: Context): SourceAvailability = AndroidSourceAvailability(context.contentResolver)

    @Provides fun projectOpener(repository: ProjectRepository, availability: SourceAvailability) = ProjectOpener(repository, availability)

    @Provides fun projectManager(repository: ProjectRepository, ids: IdGenerator, time: TimeSource) = ProjectManager(repository, ids, time)

    @Provides fun timeSource(): TimeSource = TimeSource { System.currentTimeMillis() }
}
