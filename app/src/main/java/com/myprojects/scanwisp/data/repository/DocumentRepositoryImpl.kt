package com.myprojects.scanwisp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.utils.SafeNamePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    @ApplicationContext private val context: Context,
    // START: AI_MODIFIED_BLOCK
    private val safeNamePolicy: SafeNamePolicy
    // END: AI_MODIFIED_BLOCK
) : DocumentRepository {

    private fun deleteFileFromPath(path: String) {
        try {
            val uri = Uri.parse(path)
            val file = if (uri.scheme == "file") File(uri.path!!) else File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("FileDeletion", "Failed to parse and delete file at path: $path", e)
        }
    }

    override fun getDocuments(folderId: String?): Flow<List<DocumentWithPages>> {
        return if (folderId == null) {
            dao.getOrphanDocumentsWithPages()
        } else {
            dao.getDocumentsWithPagesInFolder(folderId)
        }
    }

    override fun getDocumentById(id: String): Flow<DocumentWithPages?> {
        return dao.getDocumentWithPagesById(id)
    }

    override suspend fun createDocument(
        title: String,
        pageData: List<Pair<String, String>>,
        folderId: String?
    ) {
        if (pageData.isEmpty()) return

        val documentId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val coverPath = pageData.first().second

        val document = DocumentEntity(
            id = documentId,
            title = title,
            creationTimestamp = timestamp,
            coverImagePath = coverPath,
            folderId = folderId
        )

        val pages = pageData.mapIndexed { index, (originalPath, processedPath) ->
            PageEntity(
                documentOwnerId = documentId,
                pageNumber = index + 1,
                originalImagePath = originalPath,
                processedImagePath = processedPath,
                position = index.toLong()
            )
        }
        dao.insertDocumentAndPages(document, pages)
    }

    override suspend fun renameDocument(documentId: String, newTitle: String) {
        val document = dao.getDocumentById(documentId)
        if (document != null) {
            val updatedDocument = document.copy(title = newTitle)
            dao.updateDocument(updatedDocument)
        }
    }

    override suspend fun deleteDocumentById(documentId: String) {
        withContext(Dispatchers.IO) {
            val documentToDelete = dao.getDocumentWithPagesById(documentId).first()
            documentToDelete?.pages?.forEach { page ->
                deleteFileFromPath(page.processedImagePath)
                if (page.originalImagePath.isNotEmpty() && page.originalImagePath != page.processedImagePath) {
                    deleteFileFromPath(page.originalImagePath)
                }
            }
        }
        dao.deleteDocumentById(documentId)
    }

    override suspend fun addPagesToDocument(
        documentId: String,
        pageData: List<Pair<String, String>>
    ) {
        if (pageData.isEmpty()) return
        val documentWithPages = dao.getDocumentWithPagesById(documentId).first()
        val maxPosition = documentWithPages?.pages?.maxOfOrNull { it.position } ?: -1L

        val newPages = pageData.mapIndexed { index, (originalPath, processedPath) ->
            val newPosition = maxPosition + 1 + index
            PageEntity(
                documentOwnerId = documentId,
                pageNumber = newPosition.toInt() + 1,
                originalImagePath = originalPath,
                processedImagePath = processedPath,
                position = newPosition
            )
        }
        dao.insertPages(newPages)
    }

    override suspend fun deletePages(pageIds: List<String>) {
        if (pageIds.isEmpty()) return

        val pagesToDelete = withContext(Dispatchers.IO) {
            val pages = dao.getPagesByIds(pageIds)
            pages.forEach { page ->
                deleteFileFromPath(page.processedImagePath)
                if (page.originalImagePath.isNotEmpty() && page.originalImagePath != page.processedImagePath) {
                    deleteFileFromPath(page.originalImagePath)
                }
            }
            pages
        }

        if (pagesToDelete.isEmpty()) return

        val documentId = pagesToDelete.first().documentOwnerId
        val document = dao.getDocumentById(documentId) ?: return

        var documentToUpdate = document
        val deletedPaths = pagesToDelete.map { it.processedImagePath }.toSet()

        if (document.coverImagePath in deletedPaths) {
            val allPages = dao.getAllPagesForDocument(documentId)
            val remainingPages = allPages
                .filter { it.id !in pageIds.toSet() }
                .sortedBy { it.position }

            val newCoverPath = remainingPages.firstOrNull()?.processedImagePath ?: ""
            documentToUpdate = document.copy(coverImagePath = newCoverPath)
        }

        dao.deletePagesAndHandleCoverUpdate(documentToUpdate, pageIds)
    }

    override suspend fun updatePageOrder(reorderedPages: List<PageEntity>) {
        val updatedEntities = reorderedPages.mapIndexed { index, page ->
            page.copy(position = index.toLong())
        }
        dao.updatePages(updatedEntities)
    }

    override fun getPageById(pageId: String): Flow<PageEntity?> {
        return dao.getPageById(pageId)
    }

    override suspend fun getPagesByIds(pageIds: List<String>): List<PageEntity> {
        return dao.getPagesByIds(pageIds)
    }

    override suspend fun replacePageImage(pageId: String, newImageUri: Uri) {
        withContext(Dispatchers.IO) {
            val oldPage = dao.getPageByIdOnce(pageId) ?: return@withContext
            if (oldPage.processedImagePath.isNotEmpty()) {
                deleteFileFromPath(oldPage.processedImagePath)
            }
            if (oldPage.originalImagePath.isNotEmpty() && oldPage.originalImagePath != oldPage.processedImagePath) {
                deleteFileFromPath(oldPage.originalImagePath)
            }
            val newPath = newImageUri.toString()
            val updatedPage = oldPage.copy(
                originalImagePath = newPath,
                processedImagePath = newPath
            )
            dao.updatePage(updatedPage)
        }
    }

    override fun getAllFolders(): Flow<List<FolderEntity>> {
        return dao.getAllFolders()
    }

    override suspend fun createFolder(name: String) {
        val newFolder = FolderEntity(
            name = name,
            creationTimestamp = System.currentTimeMillis()
        )
        dao.insertFolder(newFolder)
    }

    override suspend fun moveDocumentsToFolder(documentIds: List<String>, folderId: String?) {
        dao.moveDocumentsToFolder(documentIds, folderId)
    }

    override suspend fun getFolderById(folderId: String): FolderEntity? {
        return dao.getFolderById(folderId)
    }

    override fun getFoldersWithDocumentCount(): Flow<List<FolderWithDocumentCount>> {
        return dao.getAllFoldersWithDocumentCount()
    }

    override suspend fun updateDocumentCover(documentId: String, newCoverPath: String) {
        val document = dao.getDocumentById(documentId)
        if (document != null) {
            val updatedDocument = document.copy(coverImagePath = newCoverPath)
            dao.updateDocument(updatedDocument)
        }
    }

    // START: AI_MODIFIED_BLOCK
    override suspend fun mergeDocuments(
        documentIds: List<String>,
        newTitle: String,
        folderId: String?
    ) {
        val docsToMerge = documentIds.mapNotNull { dao.getDocumentById(it) }
        val sortedDocs = docsToMerge.sortedByDescending { it.creationTimestamp }

        val allPages = sortedDocs.flatMap { doc ->
            dao.getAllPagesForDocument(doc.id).sortedBy { it.position }
        }

        if (allPages.isEmpty()) return

        val newDocumentId = UUID.randomUUID().toString()
        val newDocument = DocumentEntity(
            id = newDocumentId,
            title = newTitle,
            creationTimestamp = System.currentTimeMillis(),
            coverImagePath = allPages.first().processedImagePath,
            folderId = folderId
        )

        val newPages = allPages.mapIndexed { index, page ->
            page.copy(
                id = UUID.randomUUID().toString(),
                documentOwnerId = newDocumentId,
                position = index.toLong(),
                pageNumber = index + 1
            )
        }

        dao.mergeDocumentsAndDeleteOld(newDocument, newPages, documentIds)
    }

    override suspend fun splitPagesIntoNewDocuments(
        originalDocumentId: String,
        pageIds: List<String>,
        baseTitle: String,
        folderId: String?
    ) {
        val pagesToSplit = dao.getPagesByIds(pageIds)
        if (pagesToSplit.isEmpty()) return

        val newDocumentsAndPages = pagesToSplit.mapIndexed { index, page ->
            val newDocId = UUID.randomUUID().toString()
            val newDoc = DocumentEntity(
                id = newDocId,
                title = "$baseTitle (Часть ${index + 1})",
                creationTimestamp = System.currentTimeMillis() + index, // ensure unique sort order
                coverImagePath = page.processedImagePath,
                folderId = folderId
            )
            val updatedPage = page.copy(
                documentOwnerId = newDocId,
                position = 0,
                pageNumber = 1
            )
            newDoc to updatedPage
        }

        val document = dao.getDocumentById(originalDocumentId) ?: return
        var documentToUpdate = document
        val splitPaths = pagesToSplit.map { it.processedImagePath }.toSet()

        if (document.coverImagePath in splitPaths) {
            val allPages = dao.getAllPagesForDocument(originalDocumentId)
            val remainingPages = allPages
                .filter { it.id !in pageIds.toSet() }
                .sortedBy { it.position }
            val newCoverPath = remainingPages.firstOrNull()?.processedImagePath ?: ""
            documentToUpdate = document.copy(coverImagePath = newCoverPath)
        }
        val remainingPageCount = dao.getPageCount(originalDocumentId) - pageIds.size
        dao.splitPagesFromDocument(newDocumentsAndPages, documentToUpdate, remainingPageCount == 0)
    }
    // END: AI_MODIFIED_BLOCK
}