// Файл: app/src/main/java/com/myprojects/scanwisp/data/local/model/DocumentEntity.kt

package com.myprojects.scanwisp.data.local.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Immutable
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("folderId"),
        Index("creationTimestamp"),
        Index(value = ["folderId", "creationTimestamp"]),
        Index("title")
    ]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val creationTimestamp: Long,
    val coverImagePath: String,
    val folderId: String?,
    val deletionTimestamp: Long? = null
)