package com.myprojects.scanwisp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentFtsEntity
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.PageEntity

@Database(
    entities = [DocumentEntity::class, PageEntity::class, FolderEntity::class, DocumentFtsEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}