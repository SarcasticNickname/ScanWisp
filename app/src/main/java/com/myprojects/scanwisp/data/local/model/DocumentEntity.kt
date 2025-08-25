package com.myprojects.scanwisp.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE // При удалении папки удалятся все документы в ней!
        )
    ],
    indices = [Index("folderId")]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val creationTimestamp: Long,
    val coverImagePath: String,
    val folderId: String? // null означает, что документ находится в корне
)