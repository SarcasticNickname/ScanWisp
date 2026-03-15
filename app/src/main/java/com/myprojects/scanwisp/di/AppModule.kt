package com.myprojects.scanwisp.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.data.local.AppDatabase
import com.myprojects.scanwisp.data.local.AppDatabaseMigrations
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.PredefinedFolders
import com.myprojects.scanwisp.utils.ImageCompressionService
import com.myprojects.scanwisp.utils.ImageProcessor
import com.myprojects.scanwisp.utils.JpegExportService
import com.myprojects.scanwisp.utils.PdfExportService
import com.myprojects.scanwisp.utils.SafeNamePolicy
import com.myprojects.scanwisp.utils.ZipExportService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val roomCallback = object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL(
                    """
                    INSERT INTO folders (id, name, creationTimestamp) 
                    VALUES ('${PredefinedFolders.ARCHIVE_FOLDER_ID}', '${PredefinedFolders.ARCHIVE_FOLDER_NAME}', 0)
                    """
                )
                connection.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS pages_fts USING fts5(
                        page_id            UNINDEXED,
                        document_owner_id  UNINDEXED,
                        page_number        UNINDEXED,
                        extracted_text,
                        tokenize='unicode61 remove_diacritics 1'
                    )
                    """
                )
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "scanwisp_db"
        )
            .addCallback(roomCallback)
            .addMigrations(AppDatabaseMigrations.MIGRATION_1_2)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @Provides
    @Singleton
    fun provideDocumentDao(appDatabase: AppDatabase): DocumentDao {
        return appDatabase.documentDao()
    }

    @Provides
    @Singleton
    fun provideImageProcessor(
        @ApplicationContext context: Context,
        safeNamePolicy: SafeNamePolicy
    ): ImageProcessor {
        return ImageProcessor(context, safeNamePolicy)
    }

    @Provides
    @Singleton
    fun providePdfExportService(
        @ApplicationContext context: Context,
        imageCompressionService: ImageCompressionService,
        // imageInfoReader УБРАН ОТСЮДА
        crashlytics: FirebaseCrashlytics
    ): PdfExportService {
        // Вызываем новый конструктор без imageInfoReader
        return PdfExportService(context, imageCompressionService, crashlytics)
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

    @Provides
    @Singleton
    fun provideImageCompressionService(): ImageCompressionService {
        return ImageCompressionService()
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}