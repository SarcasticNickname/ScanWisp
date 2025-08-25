package com.myprojects.scanwisp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.local.model.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    // --- Операции с Папками ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: String)

    @Query("SELECT * FROM folders ORDER BY creationTimestamp DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Transaction
    @Query(
        """
        SELECT *, (SELECT COUNT(id) FROM documents WHERE folderId = folders.id) as documentCount
        FROM folders
        ORDER BY creationTimestamp DESC
    """
    )
    fun getAllFoldersWithDocumentCount(): Flow<List<FolderWithDocumentCount>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): FolderEntity?


    // --- Операции с Документами ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Transaction
    @Query("SELECT * FROM documents WHERE folderId IS NULL ORDER BY creationTimestamp DESC")
    fun getOrphanDocumentsWithPages(): Flow<List<DocumentWithPages>>

    @Transaction
    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY creationTimestamp DESC")
    fun getDocumentsWithPagesInFolder(folderId: String): Flow<List<DocumentWithPages>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun getDocumentWithPagesById(documentId: String): Flow<DocumentWithPages?>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentById(documentId: String): DocumentEntity?

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET folderId = :newFolderId WHERE id IN (:documentIds)")
    suspend fun moveDocumentsToFolder(documentIds: List<String>, newFolderId: String?)

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: String)

    // START: AI_MODIFIED_BLOCK
    @Query("DELETE FROM documents WHERE id IN (:documentIds)")
    suspend fun deleteDocumentsByIds(documentIds: List<String>)
    // END: AI_MODIFIED_BLOCK


    // --- Операции со Страницами ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun getPageById(pageId: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageByIdOnce(pageId: String): PageEntity?

    @Query("SELECT * FROM pages WHERE id IN (:pageIds)")
    suspend fun getPagesByIds(pageIds: List<String>): List<PageEntity>

    @Query("SELECT * FROM pages WHERE documentOwnerId = :documentId")
    suspend fun getAllPagesForDocument(documentId: String): List<PageEntity>

    @Query("SELECT COUNT(id) FROM pages WHERE documentOwnerId = :documentId")
    suspend fun getPageCount(documentId: String): Int

    @Update
    suspend fun updatePage(page: PageEntity)

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id IN (:pageIds)")
    suspend fun deletePagesByIds(pageIds: List<String>)


    // --- Транзакционные операции ---

    @Transaction
    suspend fun insertDocumentAndPages(document: DocumentEntity, pages: List<PageEntity>) {
        insertDocument(document)
        insertPages(pages)
    }

    @Transaction
    suspend fun deletePagesAndHandleCoverUpdate(
        documentToUpdate: DocumentEntity,
        pageIdsToDelete: List<String>
    ) {
        updateDocument(documentToUpdate)
        deletePagesByIds(pageIdsToDelete)
    }

    // START: AI_MODIFIED_BLOCK
    @Transaction
    suspend fun mergeDocumentsAndDeleteOld(
        newDocument: DocumentEntity,
        newPages: List<PageEntity>,
        oldDocumentIds: List<String>
    ) {
        insertDocument(newDocument)
        insertPages(newPages)
        // Физически не удаляем страницы, так как они скопированы. Удаляем только старые документы,
        // а старые страницы будут удалены каскадно.
        deleteDocumentsByIds(oldDocumentIds)
    }

    @Transaction
    suspend fun splitPagesFromDocument(
        newDocumentsAndPages: List<Pair<DocumentEntity, PageEntity>>,
        originalDocumentToUpdate: DocumentEntity,
        deleteOriginalDocument: Boolean
    ) {
        newDocumentsAndPages.forEach { (doc, page) ->
            insertDocument(doc)
            updatePage(page) // Обновляем страницу, меняя ее владельца
        }
        if (deleteOriginalDocument) {
            deleteDocumentById(originalDocumentToUpdate.id)
        } else {
            updateDocument(originalDocumentToUpdate)
        }
    }
    // END: AI_MODIFIED_BLOCK
}