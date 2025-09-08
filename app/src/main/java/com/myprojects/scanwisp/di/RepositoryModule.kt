package com.myprojects.scanwisp.di

import com.myprojects.scanwisp.data.repository.DocumentRepositoryImpl
import com.myprojects.scanwisp.data.repository.RemoteConfigRepositoryImpl
import com.myprojects.scanwisp.data.repository.SettingsRepositoryImpl
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        documentRepositoryImpl: DocumentRepositoryImpl
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(
        remoteConfigRepositoryImpl: RemoteConfigRepositoryImpl
    ): RemoteConfigRepository
}