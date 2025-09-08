package com.myprojects.scanwisp.data.local.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Immutable
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val creationTimestamp: Long
)