package com.myprojects.scanwisp.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.myprojects.scanwisp.data.local.AppDatabase
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
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Этот колбэк вызывается только при первом создании БД.
                // Он создаст предопределенную папку "Архив".
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
            .addCallback(roomCallback)
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
}