package com.myprojects.scanwisp.data.repository

import android.content.Context
import android.net.Uri
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.DocumentRow
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.SortBy
import com.myprojects.scanwisp.domain.model.SortOrder
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.utils.ImageProcessor
import com.myprojects.scanwisp.utils.SafeNamePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject

class DocumentRepositoryImpl @Inject constructor(
    private val dao: DocumentDao,
    @ApplicationContext private val context: Context,
    private val safeNamePolicy: SafeNamePolicy,
    private val imageProcessor: ImageProcessor
) : DocumentRepository {

    private fun deleteFileFromPath(path: String?) {
        if (path.isNullOrBlank() || path.isEmpty()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete file at path: $path")
        }
    }

    // Более надежная функция подготовки FTS-запроса
    private fun prepareFtsQuery(raw: String): String =
        raw.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"${it.replace("\"", "\"\"")}\"*" }

    override fun getDocumentRows(
        folderId: String?,
        query: String,
        sortBy: SortBy,
        sortOrder: SortOrder
    ): Flow<List<DocumentRow>> {
        val sortByStr = sortBy.name
        val sortOrderStr = sortOrder.name

        // Логика выбора нужного DAO-метода
        return if (query.isBlank()) {
            dao.getDocumentRowsWithoutSearch(folderId, sortByStr, sortOrderStr)
        } else {
            val ftsQuery = prepareFtsQuery(query)
            dao.getDocumentRowsWithSearch(folderId, ftsQuery, sortByStr, sortOrderStr)
        }
    }

    override fun getDocumentById(id: String): Flow<DocumentWithPages?> {
        return dao.getDocumentWithPagesById(id)
    }

    override suspend fun createDocument(
        title: String,
        sourceUris: List<Uri>,
        folderId: String?
    ) {
        if (sourceUris.isEmpty()) return

        val processedPageData = coroutineScope {
            sourceUris.map { uri ->
                async(Dispatchers.IO) {
                    imageProcessor.processImageForStorage(uri) to uri.toString()
                }
            }.awaitAll()
        }

        val validPages = processedPageData.mapNotNull { (paths, sourceUri) ->
            if (paths != null) Triple(
                paths.processedImagePath,
                paths.thumbnailPath,
                sourceUri
            ) else null
        }

        if (validPages.isEmpty()) {
            Timber.e("No pages decoded from URIs: ${sourceUris.joinToString()}")
            throw IllegalStateException("No pages decoded from scanner URIs")
        }

        val documentId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val coverPath = validPages.first().second

        val document = DocumentEntity(
            id = documentId,
            title = title,
            creationTimestamp = timestamp,
            coverImagePath = coverPath,
            folderId = folderId
        )

        val pages = validPages.mapIndexed { index, (processedPath, thumbPath, sourcePath) ->
            PageEntity(
                documentOwnerId = documentId,
                pageNumber = index + 1,
                sourceImagePath = sourcePath,
                processedImagePath = processedPath,
                thumbnailPath = thumbPath,
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
                deleteFileFromPath(page.thumbnailPath)
            }
        }
        dao.deleteDocumentById(documentId)
    }

    override suspend fun addPagesToDocument(
        documentId: String,
        sourceUris: List<Uri>
    ) {
        if (sourceUris.isEmpty()) return

        val processedPageData = coroutineScope {
            sourceUris.map { uri ->
                async(Dispatchers.IO) {
                    imageProcessor.processImageForStorage(uri) to uri.toString()
                }
            }.awaitAll()
        }

        val validPages = processedPageData.mapNotNull { (paths, sourceUri) ->
            if (paths != null) Triple(
                paths.processedImagePath,
                paths.thumbnailPath,
                sourceUri
            ) else null
        }

        if (validPages.isEmpty()) {
            Timber.e("No pages decoded from URIs: ${sourceUris.joinToString()}")
            throw IllegalStateException("No pages decoded from scanner URIs")
        }


        val documentWithPages = dao.getDocumentWithPagesById(documentId).first()
        val maxPosition = documentWithPages?.pages?.maxOfOrNull { it.position } ?: -1L

        val newPages = validPages.mapIndexed { index, (processedPath, thumbPath, sourcePath) ->
            val newPosition = maxPosition + 1 + index
            PageEntity(
                documentOwnerId = documentId,
                pageNumber = newPosition.toInt() + 1,
                sourceImagePath = sourcePath,
                processedImagePath = processedPath,
                thumbnailPath = thumbPath,
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
                deleteFileFromPath(page.thumbnailPath)
            }
            pages
        }

        if (pagesToDelete.isEmpty()) return

        val documentId = pagesToDelete.first().documentOwnerId
        val document = dao.getDocumentById(documentId) ?: return

        var documentToUpdate = document
        val deletedThumbPaths = pagesToDelete.map { it.thumbnailPath }.toSet()

        if (document.coverImagePath in deletedThumbPaths) {
            val allPages = dao.getAllPagesForDocument(documentId)
            val remainingPages = allPages
                .filter { it.id !in pageIds.toSet() }
                .sortedBy { it.position }

            val newCoverPath = remainingPages.firstOrNull()?.thumbnailPath ?: ""
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

            deleteFileFromPath(oldPage.processedImagePath)
            deleteFileFromPath(oldPage.thumbnailPath)

            val newPaths = imageProcessor.processImageForStorage(newImageUri)

            if (newPaths != null) {
                val updatedPage = oldPage.copy(
                    sourceImagePath = newImageUri.toString(),
                    processedImagePath = newPaths.processedImagePath,
                    thumbnailPath = newPaths.thumbnailPath
                )
                dao.updatePage(updatedPage)

                val document = dao.getDocumentById(oldPage.documentOwnerId)
                if (document != null && document.coverImagePath == oldPage.thumbnailPath) {
                    updateDocumentCover(document.id, newPaths.thumbnailPath)
                }
            }
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

    override suspend fun mergeDocuments(
        documentIds: List<String>,
        newTitle: String,
        folderId: String?
    ) {
        val docsToMerge = documentIds.mapNotNull { dao.getDocumentById(it) }
        val sortedDocs = docsToMerge.sortedByDescending { it.creationTimestamp }

        val allPages = sortedDocs.flatMap { doc ->
            dao.getAllPagesForDocument(doc.id)
        }

        if (allPages.isEmpty()) return

        val newDocumentId = UUID.randomUUID().toString()
        val newDocument = DocumentEntity(
            id = newDocumentId,
            title = newTitle,
            creationTimestamp = System.currentTimeMillis(),
            coverImagePath = allPages.first().thumbnailPath,
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
                creationTimestamp = System.currentTimeMillis() + index,
                coverImagePath = page.thumbnailPath,
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
        val splitThumbPaths = pagesToSplit.map { it.thumbnailPath }.toSet()

        if (document.coverImagePath in splitThumbPaths) {
            val allPages = dao.getAllPagesForDocument(originalDocumentId)
            val remainingPages = allPages
                .filter { it.id !in pageIds.toSet() }
                .sortedBy { it.position }
            val newCoverPath = remainingPages.firstOrNull()?.thumbnailPath ?: ""
            documentToUpdate = document.copy(coverImagePath = newCoverPath)
        }
        val remainingPageCount = dao.getPageCount(originalDocumentId) - pageIds.size
        dao.splitPagesFromDocument(newDocumentsAndPages, documentToUpdate, remainingPageCount == 0)
    }

    override fun getDeletedDocumentRows(): Flow<List<DocumentRow>> {
        return dao.getDeletedDocumentRows()
    }

    override suspend fun restoreDocument(documentId: String) {
        val document = dao.getDocumentById(documentId)
        if (document != null) {
            // Восстанавливаем в оригинальную папку или в корень, если ее удалили
            val originalFolderExists = document.folderId?.let { dao.getFolderById(it) } != null
            if (document.folderId != null && originalFolderExists) {
                dao.restoreDocumentToFolder(documentId, document.folderId)
            } else {
                dao.restoreDocumentToRoot(documentId)
            }
        }
    }

    override suspend fun deleteDocumentsPermanently(documentIds: List<String>) {
        if (documentIds.isEmpty()) return

        // Сначала удаляем связанные файлы
        withContext(Dispatchers.IO) {
            documentIds.forEach { docId ->
                val docWithPages = dao.getDocumentWithPagesById(docId).first()
                docWithPages?.pages?.forEach { page ->
                    deleteFileFromPath(page.processedImagePath)
                    deleteFileFromPath(page.thumbnailPath)
                }
            }
        }
        // Затем удаляем записи из БД
        dao.deleteDocumentsByIds(documentIds)
    }
}
