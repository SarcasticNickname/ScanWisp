package com.myprojects.scanwisp.data.repository

import android.net.Uri
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/**
 * Полнофункциональная фейковая реализация репозитория для unit-тестов.
 * Хранит данные в памяти и имитирует операции CRUD.
 */
class FakeDocumentRepository : DocumentRepository {

    // Внутреннее "in-memory" хранилище
    private val documentsFlow = MutableStateFlow<List<DocumentEntity>>(emptyList())
    private val pagesFlow = MutableStateFlow<List<PageEntity>>(emptyList())
    private val foldersFlow = MutableStateFlow<List<FolderEntity>>(emptyList())

    // --- Методы для управления состоянием в тестах ---
    fun insertDocuments(docs: List<DocumentEntity>) {
        documentsFlow.value = docs
    }

    fun insertPages(pages: List<PageEntity>) {
        this.pagesFlow.value = pages
    }

    fun insertFolders(folders: List<FolderEntity>) {
        this.foldersFlow.value = folders
    }

    fun clearAll() {
        documentsFlow.value = emptyList()
        pagesFlow.value = emptyList()
        foldersFlow.value = emptyList()
    }

    // --- Реализация интерфейса DocumentRepository ---

    override fun getDocumentRows(
        folderId: String?,
        query: String,
        sortBy: SortBy,
        sortOrder: SortOrder
    ): Flow<List<DocumentRow>> {
        return combine(documentsFlow, pagesFlow) { docs, pages ->
            docs.filter { it.deletionTimestamp == null }
                .filter { it.folderId == folderId }
                .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
                .map { doc ->
                    DocumentRow(
                        id = doc.id,
                        title = doc.title,
                        coverImagePath = doc.coverImagePath,
                        creationTimestamp = doc.creationTimestamp,
                        pageCount = pages.count { it.documentOwnerId == doc.id },
                        folderId = doc.folderId
                    )
                }
                .let {
                    when (sortBy) {
                        SortBy.DATE -> it.sortedBy { row -> row.creationTimestamp }
                        SortBy.NAME -> it.sortedBy { row -> row.title.lowercase() }
                    }
                }
                .let {
                    if (sortOrder == SortOrder.DESCENDING) it.reversed() else it
                }
        }
    }

    override fun getDocumentById(id: String): Flow<DocumentWithPages?> {
        return combine(documentsFlow, pagesFlow) { docs, pages ->
            docs.find { it.id == id && it.deletionTimestamp == null }?.let { doc ->
                DocumentWithPages(
                    document = doc,
                    pages = pages.filter { it.documentOwnerId == id }.sortedBy { it.position }
                )
            }
        }
    }

    override suspend fun createDocument(title: String, sourceUris: List<Uri>, folderId: String?) {
        val docId = UUID.randomUUID().toString()
        val newDoc = DocumentEntity(
            id = docId,
            title = title,
            creationTimestamp = System.currentTimeMillis(),
            coverImagePath = "cover_$docId",
            folderId = folderId
        )
        documentsFlow.value += newDoc

        val newPages = sourceUris.mapIndexed { index, _ ->
            PageEntity(
                id = UUID.randomUUID().toString(),
                documentOwnerId = docId,
                pageNumber = index + 1,
                sourceImagePath = "", processedImagePath = "", thumbnailPath = "thumb_$docId",
                position = index.toLong()
            )
        }
        pagesFlow.value += newPages
    }

    override suspend fun renameDocument(documentId: String, newTitle: String) {
        documentsFlow.value = documentsFlow.value.map {
            if (it.id == documentId) it.copy(title = newTitle) else it
        }
    }

    override suspend fun deleteDocumentsPermanently(documentIds: List<String>) {
        documentsFlow.value = documentsFlow.value.filterNot { it.id in documentIds }
        pagesFlow.value = pagesFlow.value.filterNot { it.documentOwnerId in documentIds }
    }

    override suspend fun restoreDocument(documentId: String) {
        documentsFlow.value = documentsFlow.value.map {
            if (it.id == documentId) it.copy(deletionTimestamp = null) else it
        }
    }

    override fun getDeletedDocumentRows(): Flow<List<DocumentRow>> {
        return combine(documentsFlow, pagesFlow) { docs, pages ->
            docs.filter { it.deletionTimestamp != null }
                .map { doc ->
                    DocumentRow(
                        id = doc.id,
                        title = doc.title,
                        coverImagePath = doc.coverImagePath,
                        creationTimestamp = doc.creationTimestamp,
                        pageCount = pages.count { it.documentOwnerId == doc.id },
                        folderId = doc.folderId
                    )
                }.sortedByDescending { it.creationTimestamp }
        }
    }

    override suspend fun createFolder(name: String) {
        val newFolder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            creationTimestamp = System.currentTimeMillis()
        )
        foldersFlow.value += newFolder
    }

    override suspend fun moveDocumentsToFolder(documentIds: List<String>, folderId: String?) {
        documentsFlow.value = documentsFlow.value.map {
            if (it.id in documentIds) it.copy(folderId = folderId) else it
        }
    }

    override suspend fun getFolderById(folderId: String): FolderEntity? {
        return foldersFlow.value.find { it.id == folderId }
    }

    override fun getAllFolders(): Flow<List<FolderEntity>> = foldersFlow

    override fun getFoldersWithDocumentCount(): Flow<List<FolderWithDocumentCount>> {
        return combine(foldersFlow, documentsFlow) { folders, docs ->
            folders.map { folder ->
                FolderWithDocumentCount(
                    folder = folder,
                    documentCount = docs.count { it.folderId == folder.id && it.deletionTimestamp == null }
                )
            }
        }
    }

    // --- Методы, которые не критичны для ViewModel тестов, можно оставить с TODO ---
    override suspend fun deleteDocumentById(documentId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun addPagesToDocument(documentId: String, sourceUris: List<Uri>) {
        TODO("Not yet implemented")
    }

    override suspend fun deletePages(pageIds: List<String>) {
        TODO("Not yet implemented")
    }

    override suspend fun updatePageOrder(reorderedPages: List<PageEntity>) {
        TODO("Not yet implemented")
    }

    override fun getPageById(pageId: String): Flow<PageEntity?> = MutableStateFlow(null)
    override suspend fun getPagesByIds(pageIds: List<String>): List<PageEntity> = emptyList()
    override suspend fun replacePageImage(pageId: String, newImageUri: Uri) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDocumentCover(documentId: String, newCoverPath: String) {
        TODO("Not yet implemented")
    }

    override suspend fun mergeDocuments(
        documentIds: List<String>,
        newTitle: String,
        folderId: String?
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun splitPagesIntoNewDocuments(
        originalDocumentId: String,
        pageIds: List<String>,
        baseTitle: String,
        folderId: String?
    ) {
        TODO("Not yet implemented")
    }
}