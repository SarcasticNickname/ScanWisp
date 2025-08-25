package com.myprojects.scanwisp.data.local.model

import androidx.room.Embedded

/**
 * Класс-отношение для получения папки вместе с количеством документов в ней
 * одним запросом к базе данных.
 */
data class FolderWithDocumentCount(
    @Embedded
    val folder: FolderEntity,
    val documentCount: Int
)