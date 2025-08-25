package com.myprojects.scanwisp.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentOwnerId"],
            onDelete = ForeignKey.CASCADE // При удалении документа удалятся все его страницы.
        )
    ],
    indices = [Index("documentOwnerId")] // Индекс для ускорения запросов по ID владельца
)
data class PageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val documentOwnerId: String,
    val pageNumber: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val position: Long
)