package com.myprojects.scanwisp.data.local

/**
 * Результат полнотекстового поиска по OCR-тексту страниц.
 *
 * Возвращается запросом к FTS5-таблице pages_fts + JOIN с documents.
 * [snippet] содержит фрагмент текста с маркерами совпадений «<<» и «>>».
 */
data class PageSearchResult(
    val pageId: String,
    val documentId: String,
    val pageNumber: Int,
    val documentTitle: String,
    val coverImagePath: String,
    val snippet: String
)