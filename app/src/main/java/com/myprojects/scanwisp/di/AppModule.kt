package com.myprojects.scanwisp.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myprojects.scanwisp.data.local.AppDatabase
import com.myprojects.scanwisp.data.local.DocumentDao
// START: AI_MODIFIED_BLOCK
import com.myprojects.scanwisp.data.local.model.PredefinedFolders
// END: AI_MODIFIED_BLOCK
import com.myprojects.scanwisp.utils.ImageCompressionService
import com.myprojects.scanwisp.utils.ImageInfoReader
import com.myprojects.scanwisp.utils.JpegExportService
import com.myprojects.scanwisp.utils.PdfExportService
import com.myprojects.scanwisp.utils.ZipExportService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-модуль для предоставления зависимостей уровня приложения.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // START: AI_MODIFIED_BLOCK
        /**
         * Callback для предварительного заполнения базы данных при ее первом создании.
         */
        val roomCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Выполняем SQL-запрос для добавления папки "Архив"
                // Это произойдет только один раз при установке приложения.
                db.execSQL(
                    """
                    INSERT INTO folders (id, name, creationTimestamp) 
                    VALUES ('${PredefinedFolders.ARCHIVE_FOLDER_ID}', '${PredefinedFolders.ARCHIVE_FOLDER_NAME}', 0)
                    """
                )
            }
        }
        // END: AI_MODIFIED_BLOCK

        // Пустая миграция как заглушка, чтобы можно было добавлять настоящие.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // В будущем здесь будут SQL-запросы для изменения схемы
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "scanwisp_db"
        )
            // .fallbackToDestructiveMigration() // УБИРАЕМ ДЕСТРУКТИВНУЮ МИГРАЦИЮ
            // .addMigrations(MIGRATION_3_4) // Заготовка для будущих миграций
            .addCallback(roomCallback) // <-- ДОБАВЛЯЕМ НАШ CALLBACK
            .build()
    }

    @Provides
    @Singleton
    fun provideDocumentDao(appDatabase: AppDatabase): DocumentDao {
        return appDatabase.documentDao()
    }

    /**
     * Предоставляет экземпляр PdfExportService.
     * Hilt автоматически предоставит ApplicationContext и ImageCompressionService.
     */
    @Provides
    @Singleton
    fun providePdfExportService(
        @ApplicationContext context: Context,
        imageCompressionService: ImageCompressionService,
        imageInfoReader: ImageInfoReader
    ): PdfExportService {
        return PdfExportService(context, imageCompressionService, imageInfoReader)
    }

    @Provides
    @Singleton
    fun provideZipExportService(@ApplicationContext context: Context): ZipExportService {
        return ZipExportService(context)
    }

    @Provides
    @Singleton
    fun provideJpegExportService(@ApplicationContext context: Context): JpegExportService {
        return JpegExportService(context)
    }

    /**
     * Предоставляет экземпляр ImageCompressionService.
     */
    @Provides
    @Singleton
    fun provideImageCompressionService(): ImageCompressionService {
        return ImageCompressionService()
    }
}