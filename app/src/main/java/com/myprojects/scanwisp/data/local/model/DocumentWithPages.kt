package com.myprojects.scanwisp.data.local.model

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Relation

/**
 * Класс-отношение для получения документа вместе со списком всех его страниц
 * одним запросом к базе данных.
 */
@Immutable
data class DocumentWithPages(
    @Embedded
    val document: DocumentEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "documentOwnerId"
    )
    val pages: List<PageEntity>
)