package com.myprojects.scanwisp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.OcrStatus

// pages_fts — виртуальная FTS5-таблица, создаётся в AppDatabaseMigrations.MIGRATION_1_2
@Database(
    entities = [DocumentEntity::class, PageEntity::class, FolderEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(OcrStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}

class OcrStatusConverter {
    @TypeConverter
    fun fromOcrStatus(value: OcrStatus): String = value.name

    @TypeConverter
    fun toOcrStatus(value: String): OcrStatus =
        runCatching { OcrStatus.valueOf(value) }.getOrDefault(OcrStatus.PENDING)
}