package com.myprojects.scanwisp.data.local.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
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
        Index(value = ["id"], unique = true),
        Index("folderId"),
        Index("creationTimestamp"),
        Index(value = ["folderId", "creationTimestamp"]),
        Index("title"),
        Index("deletionTimestamp")
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val pk: Long = 0,
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val creationTimestamp: Long,
    val coverImagePath: String,
    val folderId: String?,
    val deletionTimestamp: Long? = null
)

@Entity(tableName = "documents_fts")
@Fts4(
    contentEntity = DocumentEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3, 4]
)
data class DocumentFtsEntity(
    val title: String,
)