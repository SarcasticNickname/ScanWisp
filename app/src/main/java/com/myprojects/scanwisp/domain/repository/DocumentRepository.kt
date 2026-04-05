package com.myprojects.scanwisp.domain.repository

import android.net.Uri
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.data.local.PageSearchResult
import com.myprojects.scanwisp.data.local.TrashDocumentRow
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {

    fun getDocumentRows(
        folderId: String?,
        query: String,
        sortBy: SortBy,
        sortOrder: SortOrder
    ): Flow<List<DocumentRow>>

    /**
     * Полнотекстовый поиск по OCR-тексту страниц.
     * Возвращает результаты из FTS5-таблицы pages_fts.
     * Пустой запрос → пустой список.
     */
    fun searchByContent(query: String): Flow<List<PageSearchResult>>


    fun getDocumentById(id: String): Flow<DocumentWithPages?>
    suspend fun createDocument(title: String, sourceUris: List<Uri>, folderId: String?): String

    suspend fun renameDocument(documentId: String, newTitle: String)
    suspend fun deleteDocumentById(documentId: String)
    suspend fun addPagesToDocument(documentId: String, sourceUris: List<Uri>): String
    suspend fun deletePages(pageIds: List<String>)
    suspend fun updatePageOrder(reorderedPages: List<PageEntity>)
    fun getPageById(pageId: String): Flow<PageEntity?>
    suspend fun getPagesByIds(pageIds: List<String>): List<PageEntity>
    suspend fun replacePageImage(pageId: String, newImageUri: Uri)
    suspend fun rotatePage(pageId: String, degrees: Float)
    suspend fun getAdjacentPageIds(documentOwnerId: String, currentPosition: Long): Pair<String?, String?>
    suspend fun getPageCount(documentOwnerId: String): Int
    fun getAllFolders(): Flow<List<FolderEntity>>
    suspend fun createFolder(name: String)
    suspend fun renameFolder(folderId: String, newName: String)
    suspend fun deleteFolder(folderId: String)
    suspend fun moveDocumentsToFolder(documentIds: List<String>, folderId: String?)
    suspend fun getFolderById(folderId: String): FolderEntity?
    fun getFoldersWithDocumentCount(): Flow<List<FolderWithDocumentCount>>
    suspend fun updateDocumentCover(documentId: String, newCoverPath: String)

    fun getDeletedDocumentRows(): Flow<List<DocumentRow>>
    fun getDeletedDocumentsWithDate(): Flow<List<TrashDocumentRow>>
    suspend fun restoreDocument(documentId: String)
    suspend fun deleteDocumentsPermanently(documentIds: List<String>)

    suspend fun mergeDocuments(documentIds: List<String>, newTitle: String, folderId: String?)
    suspend fun splitPagesIntoNewDocuments(
        originalDocumentId: String,
        pageIds: List<String>,
        baseTitle: String,
        folderId: String?
    )
}