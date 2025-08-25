package com.myprojects.scanwisp.domain.repository

import android.net.Uri
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.local.model.PageEntity
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getDocuments(folderId: String?): Flow<List<DocumentWithPages>>
    fun getDocumentById(id: String): Flow<DocumentWithPages?>
    suspend fun createDocument(
        title: String,
        pageData: List<Pair<String, String>>,
        folderId: String?
    )

    suspend fun renameDocument(documentId: String, newTitle: String)
    suspend fun deleteDocumentById(documentId: String)
    suspend fun addPagesToDocument(documentId: String, pageData: List<Pair<String, String>>)
    suspend fun deletePages(pageIds: List<String>)
    suspend fun updatePageOrder(reorderedPages: List<PageEntity>)
    fun getPageById(pageId: String): Flow<PageEntity?>
    suspend fun getPagesByIds(pageIds: List<String>): List<PageEntity>
    suspend fun replacePageImage(pageId: String, newImageUri: Uri)
    fun getAllFolders(): Flow<List<FolderEntity>>
    suspend fun createFolder(name: String)
    suspend fun moveDocumentsToFolder(documentIds: List<String>, folderId: String?)
    suspend fun getFolderById(folderId: String): FolderEntity?
    fun getFoldersWithDocumentCount(): Flow<List<FolderWithDocumentCount>>
    suspend fun updateDocumentCover(documentId: String, newCoverPath: String)

    // START: AI_MODIFIED_BLOCK
    suspend fun mergeDocuments(documentIds: List<String>, newTitle: String, folderId: String?)
    suspend fun splitPagesIntoNewDocuments(
        originalDocumentId: String,
        pageIds: List<String>,
        baseTitle: String,
        folderId: String?
    )
    // END: AI_MODIFIED_BLOCK
}