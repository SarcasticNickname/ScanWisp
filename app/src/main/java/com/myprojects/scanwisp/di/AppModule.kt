package com.myprojects.scanwisp.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.data.local.AppDatabase
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.PredefinedFolders
import com.myprojects.scanwisp.utils.ImageCompressionService
import com.myprojects.scanwisp.utils.ImageInfoReader
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

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_creationTimestamp ON documents(creationTimestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_folderId_creationTs ON documents(folderId, creationTimestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pages_owner_position ON pages(documentOwnerId, position)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE pages RENAME COLUMN originalImagePath TO sourceImagePath;")
            db.execSQL("ALTER TABLE pages ADD COLUMN thumbnailPath TEXT NOT NULL DEFAULT '';")
            /**
             * ==========================================================
             * ДОПОЛНЕНИЕ: Добавляем создание индекса для поиска по названию.
             * Технически это должно быть в миграции 5->6, но для удобства
             * объединяем с текущей, пока приложение не в релизе.
             * ==========================================================
             */
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_title ON documents(title)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE documents ADD COLUMN deletionTimestamp INTEGER DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val roomCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(
                    """
                    INSERT INTO folders (id, name, creationTimestamp) 
                    VALUES ('${PredefinedFolders.ARCHIVE_FOLDER_ID}', '${PredefinedFolders.ARCHIVE_FOLDER_NAME}', 0)
                    """
                )
            }
        }

        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "scanwisp_db"
        )
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .addCallback(roomCallback)
            .build()
    }

    @Provides
    @Singleton
    fun provideDocumentDao(appDatabase: AppDatabase): DocumentDao {
        return appDatabase.documentDao()
    }

    /**
     * ==========================================================
     * НОВЫЙ ПРОВАЙДЕР: Внедряем наш ImageProcessor.
     * ==========================================================
     */
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
        imageInfoReader: ImageInfoReader,
        crashlytics: FirebaseCrashlytics
    ): PdfExportService {
        return PdfExportService(context, imageCompressionService, imageInfoReader, crashlytics)
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
}