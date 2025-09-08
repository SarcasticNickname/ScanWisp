package com.myprojects.scanwisp.data.local.model

import androidx.compose.runtime.Immutable
import androidx.room.Embedded

/**
 * Класс-отношение для получения папки вместе с количеством документов в ней
 * одним запросом к базе данных.
 */
@Immutable
data class FolderWithDocumentCount(
    @Embedded
    val folder: FolderEntity,
    val documentCount: Int
)