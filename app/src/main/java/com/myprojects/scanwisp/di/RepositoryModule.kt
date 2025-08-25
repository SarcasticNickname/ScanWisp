package com.myprojects.scanwisp.di

import com.myprojects.scanwisp.data.repository.DocumentRepositoryImpl
// START: AI_MODIFIED_BLOCK
import com.myprojects.scanwisp.data.repository.SettingsRepositoryImpl
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
// END: AI_MODIFIED_BLOCK
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Можно создать новый модуль для репозиториев
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository

    // START: AI_MODIFIED_BLOCK
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
// END: AI_MODIFIED_BLOCK
}