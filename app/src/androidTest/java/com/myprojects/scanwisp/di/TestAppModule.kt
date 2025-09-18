package com.myprojects.scanwisp.di

import com.myprojects.scanwisp.data.repository.FakeDocumentRepository
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class] // Заменяем реальный модуль репозиториев
)
abstract class TestAppModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        fakeDocumentRepository: FakeDocumentRepository
    ): DocumentRepository

    // Для Settings и RemoteConfig мы можем просто подставить пустые моки,
    // если их поведение не критично для UI-теста.
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepository: SettingsRepository
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(
        remoteConfigRepository: RemoteConfigRepository
    ): RemoteConfigRepository
}

// Hilt'у нужно знать, как создавать экземпляры.
// Предоставляем FakeDocumentRepository и моки.
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class] // Можно заменить и AppModule, если нужно подменить и другие зависимости
)
object TestSingletonModule {

    @Provides
    @Singleton
    fun provideFakeDocumentRepository(): FakeDocumentRepository = FakeDocumentRepository()

    @Provides
    @Singleton
    fun provideMockSettingsRepository(): SettingsRepository = mockk(relaxed = true)

    @Provides
    @Singleton
    fun provideMockRemoteConfigRepository(): RemoteConfigRepository = mockk(relaxed = true)
}