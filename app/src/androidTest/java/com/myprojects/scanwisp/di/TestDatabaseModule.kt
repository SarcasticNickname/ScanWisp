package com.myprojects.scanwisp.di

import android.content.Context
import androidx.room.Room
import com.myprojects.scanwisp.data.local.AppDatabase
import com.myprojects.scanwisp.data.local.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class] // Мы заменяем наш основной AppModule
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideInMemoryAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // В тестах разрешаем запросы в основном потоке
            .build()
    }

    @Provides
    @Singleton
    fun provideDocumentDao(appDatabase: AppDatabase): DocumentDao {
        return appDatabase.documentDao()
    }

    // Остальные зависимости, которые могут понадобиться,
    // можно предоставить здесь же, если они есть в AppModule.
    // Например, ImageProcessor, ExportServices и т.д.
    // Для нашего UI-теста пока достаточно БД.
}