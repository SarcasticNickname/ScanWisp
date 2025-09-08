// Файл: app/src/main/java/com/myprojects/scanwisp/data/local/model/PageEntity.kt

package com.myprojects.scanwisp.data.local.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Immutable
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentOwnerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentOwnerId"),
        Index(value = ["documentOwnerId", "position"])
    ]
)
data class PageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val documentOwnerId: String,
    val pageNumber: Int,
    val sourceImagePath: String,
    val processedImagePath: String,
    val thumbnailPath: String,
    val position: Long
)