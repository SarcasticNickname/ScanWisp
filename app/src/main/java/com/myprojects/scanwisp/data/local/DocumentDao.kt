package com.myprojects.scanwisp.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.SkipQueryVerification
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.DocumentWithPages
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.FolderWithDocumentCount
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.OcrStatus
import kotlinx.coroutines.flow.Flow

@Immutable
data class DocumentRow(
    val id: String,
    val title: String,
    val coverImagePath: String,
    val creationTimestamp: Long,
    val pageCount: Int,
    val folderId: String?,
    val ocrDoneCount: Int
)

/** Расширенная строка для Корзины — содержит дату удаления */
@Immutable
data class TrashDocumentRow(
    val id: String,
    val title: String,
    val coverImagePath: String,
    val creationTimestamp: Long,
    val pageCount: Int,
    val deletionTimestamp: Long
)

@Dao
interface DocumentDao {

    // --- Папки ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: String)

    @Query("SELECT * FROM folders ORDER BY creationTimestamp DESC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Transaction
    @Query("""
        SELECT *,
            (SELECT COUNT(id) FROM documents
             WHERE folderId = folders.id AND deletionTimestamp IS NULL) as documentCount
        FROM folders
        ORDER BY creationTimestamp DESC
    """)
    fun getAllFoldersWithDocumentCount(): Flow<List<FolderWithDocumentCount>>

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): FolderEntity?


    // --- Документы ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    /** Список документов БЕЗ поискового фильтра */
    @Transaction
    @Query("""
        SELECT d.id, d.title, d.coverImagePath, d.creationTimestamp, d.folderId, 
               IFNULL(p.pageCount, 0) AS pageCount,
               IFNULL(p.ocrDoneCount, 0) AS ocrDoneCount
        FROM documents d
        LEFT JOIN (
            SELECT documentOwnerId,
                   COUNT(id) AS pageCount,
                   SUM(CASE WHEN ocrStatus = 'DONE' OR (extractedText IS NOT NULL AND extractedText != '') THEN 1 ELSE 0 END) AS ocrDoneCount
            FROM pages GROUP BY documentOwnerId
        ) p ON d.id = p.documentOwnerId
        WHERE
            (d.folderId = :folderId OR (:folderId IS NULL AND d.folderId IS NULL))
            AND d.deletionTimestamp IS NULL
        ORDER BY
            CASE WHEN :sortBy = 'DATE' AND :sortOrder = 'ASC'  THEN d.creationTimestamp END ASC,
            CASE WHEN :sortBy = 'DATE' AND :sortOrder = 'DESC' THEN d.creationTimestamp END DESC,
            CASE WHEN :sortBy = 'NAME' AND :sortOrder = 'ASC'  THEN d.title END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'NAME' AND :sortOrder = 'DESC' THEN d.title END COLLATE NOCASE DESC
    """)
    fun getDocumentRowsWithoutSearch(
        folderId: String?,
        sortBy: String,
        sortOrder: String
    ): Flow<List<DocumentRow>>

    /** Поиск по названию через LIKE — substring, регистронезависимый */
    @Transaction
    @Query("""
        SELECT d.id, d.title, d.coverImagePath, d.creationTimestamp, d.folderId,
               IFNULL(p.pageCount, 0) AS pageCount,
               IFNULL(p.ocrDoneCount, 0) AS ocrDoneCount
        FROM documents d
        LEFT JOIN (
            SELECT documentOwnerId,
                   COUNT(id) AS pageCount,
                   SUM(CASE WHEN ocrStatus = 'DONE' OR (extractedText IS NOT NULL AND extractedText != '') THEN 1 ELSE 0 END) AS ocrDoneCount
            FROM pages GROUP BY documentOwnerId
        ) p ON d.id = p.documentOwnerId
        WHERE
            (d.folderId = :folderId OR (:folderId IS NULL AND d.folderId IS NULL))
            AND d.deletionTimestamp IS NULL
            AND d.title LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN :sortBy = 'DATE' AND :sortOrder = 'ASC'  THEN d.creationTimestamp END ASC,
            CASE WHEN :sortBy = 'DATE' AND :sortOrder = 'DESC' THEN d.creationTimestamp END DESC,
            CASE WHEN :sortBy = 'NAME' AND :sortOrder = 'ASC'  THEN d.title END COLLATE NOCASE ASC,
            CASE WHEN :sortBy = 'NAME' AND :sortOrder = 'DESC' THEN d.title END COLLATE NOCASE DESC
    """)
    fun getDocumentRowsWithSearch(
        folderId: String?,
        query: String,
        sortBy: String,
        sortOrder: String
    ): Flow<List<DocumentRow>>

    @Transaction
    @Query("""
        SELECT d.id, d.title, d.coverImagePath, d.creationTimestamp, d.folderId,
               IFNULL(p.pageCount, 0) as pageCount,
               IFNULL(p.ocrDoneCount, 0) as ocrDoneCount
        FROM documents d
        LEFT JOIN (
            SELECT documentOwnerId,
                   COUNT(id) as pageCount,
                   SUM(CASE WHEN ocrStatus = 'DONE' OR (extractedText IS NOT NULL AND extractedText != '') THEN 1 ELSE 0 END) AS ocrDoneCount
            FROM pages GROUP BY documentOwnerId
        ) p ON d.id = p.documentOwnerId
        WHERE d.deletionTimestamp IS NOT NULL
        ORDER BY d.deletionTimestamp DESC
    """)
    fun getDeletedDocumentRows(): Flow<List<DocumentRow>>

    @Query("""
        SELECT d.id, d.title, d.coverImagePath, d.creationTimestamp, d.deletionTimestamp,
               IFNULL(p.pageCount, 0) AS pageCount
        FROM documents d
        LEFT JOIN (
            SELECT documentOwnerId, COUNT(id) AS pageCount FROM pages GROUP BY documentOwnerId
        ) p ON d.id = p.documentOwnerId
        WHERE d.deletionTimestamp IS NOT NULL
        ORDER BY d.deletionTimestamp DESC
    """)
    fun getDeletedDocumentsWithDate(): Flow<List<TrashDocumentRow>>

    @Query("UPDATE documents SET deletionTimestamp = NULL, folderId = :folderId WHERE id = :documentId")
    suspend fun restoreDocumentToFolder(documentId: String, folderId: String)

    @Query("UPDATE documents SET deletionTimestamp = NULL, folderId = NULL WHERE id = :documentId")
    suspend fun restoreDocumentToRoot(documentId: String)

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId AND deletionTimestamp IS NULL")
    fun getDocumentWithPagesById(documentId: String): Flow<DocumentWithPages?>

    @Query("SELECT * FROM documents WHERE id = :documentId AND deletionTimestamp IS NULL LIMIT 1")
    suspend fun getDocumentById(documentId: String): DocumentEntity?

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET folderId = :newFolderId WHERE id IN (:documentIds)")
    suspend fun moveDocumentsToFolder(documentIds: List<String>, newFolderId: String?)

    @Query("UPDATE documents SET deletionTimestamp = :timestamp WHERE id IN (:documentIds)")
    suspend fun markDocumentsForDeletion(documentIds: List<String>, timestamp: Long)

    @Query("UPDATE documents SET deletionTimestamp = NULL WHERE id IN (:documentIds)")
    suspend fun unmarkDocumentsForDeletion(documentIds: List<String>)

    @Query("SELECT * FROM documents WHERE deletionTimestamp IS NOT NULL AND deletionTimestamp < :cutoffTimestamp")
    suspend fun getDocumentsPendingDeletion(cutoffTimestamp: Long): List<DocumentEntity>

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteDocumentById(documentId: String)

    @Query("DELETE FROM documents WHERE id IN (:documentIds)")
    suspend fun deleteDocumentsByIds(documentIds: List<String>)


    // --- Страницы ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun getPageById(pageId: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageByIdOnce(pageId: String): PageEntity?

    @Query("SELECT * FROM pages WHERE id IN (:pageIds)")
    suspend fun getPagesByIds(pageIds: List<String>): List<PageEntity>

    @Query("SELECT * FROM pages WHERE documentOwnerId = :documentId ORDER BY position ASC")
    suspend fun getAllPagesForDocument(documentId: String): List<PageEntity>

    @Query("SELECT COUNT(id) FROM pages WHERE documentOwnerId = :documentId")
    suspend fun getPageCount(documentId: String): Int

    @Query("""
        SELECT id FROM pages
        WHERE documentOwnerId = :documentOwnerId AND position < :currentPosition
        ORDER BY position DESC LIMIT 1
    """)
    suspend fun getPrevPageId(documentOwnerId: String, currentPosition: Long): String?

    @Query("""
        SELECT id FROM pages
        WHERE documentOwnerId = :documentOwnerId AND position > :currentPosition
        ORDER BY position ASC LIMIT 1
    """)
    suspend fun getNextPageId(documentOwnerId: String, currentPosition: Long): String?

    @Update
    suspend fun updatePage(page: PageEntity)

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id IN (:pageIds)")
    suspend fun deletePagesByIds(pageIds: List<String>)

    @Query("SELECT * FROM pages WHERE documentOwnerId = :documentId AND ocrStatus = 'PENDING' ORDER BY position ASC")
    suspend fun getPendingPagesForDocument(documentId: String): List<PageEntity>

    @Query("UPDATE pages SET ocrStatus = :status WHERE id = :pageId")
    suspend fun updatePageOcrStatus(pageId: String, status: OcrStatus)


    // --- FTS5 pages_fts: синхронизация ---
    // @SkipQueryVerification отключает compile-time проверку схемы для виртуальной таблицы,
    // не зарегистрированной как Room entity.

    @SkipQueryVerification
    @Query("DELETE FROM pages_fts WHERE page_id = :pageId")
    suspend fun deleteFtsPageEntry(pageId: String)

    @SkipQueryVerification
    @Query("""
        INSERT INTO pages_fts(page_id, document_owner_id, page_number, extracted_text)
        VALUES (:pageId, :documentOwnerId, :pageNumber, :text)
    """)
    suspend fun insertFtsPageEntry(
        pageId: String,
        documentOwnerId: String,
        pageNumber: Int,
        text: String
    )

    @Transaction
    suspend fun upsertFtsPageEntry(
        pageId: String,
        documentOwnerId: String,
        pageNumber: Int,
        text: String
    ) {
        deleteFtsPageEntry(pageId)
        insertFtsPageEntry(pageId, documentOwnerId, pageNumber, text)
    }

    /** Удаляем все FTS-записи страниц документа (при удалении документа) */
    @SkipQueryVerification
    @Query("DELETE FROM pages_fts WHERE document_owner_id = :documentId")
    suspend fun deleteFtsEntriesForDocument(documentId: String)

    /** Удаляем FTS-записи конкретных страниц (при удалении страниц) */
    @SkipQueryVerification
    @Query("DELETE FROM pages_fts WHERE page_id IN (:pageIds)")
    suspend fun deleteFtsEntriesForPages(pageIds: List<String>)


    // --- FTS5 pages_fts: полнотекстовый поиск ---
    // Запрос строится в DocumentRepositoryImpl, передаётся как SimpleSQLiteQuery.
    // observedEntities → Flow автоматически переиздаётся при изменении pages или documents.
    @RawQuery(observedEntities = [PageEntity::class, DocumentEntity::class])
    fun searchPagesByContentRaw(query: SupportSQLiteQuery): Flow<List<PageSearchResult>>

    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentByIdAny(documentId: String): DocumentEntity?

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun getDocumentWithPagesByIdAny(documentId: String): Flow<DocumentWithPages?>


    // --- Транзакции ---

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

    @Transaction
    suspend fun mergeDocumentsAndDeleteOld(
        newDocument: DocumentEntity,
        newPages: List<PageEntity>,
        oldDocumentIds: List<String>
    ) {
        insertDocument(newDocument)
        insertPages(newPages)
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
            updatePage(page)
        }
        if (deleteOriginalDocument) {
            deleteDocumentById(originalDocumentToUpdate.id)
        } else {
            updateDocument(originalDocumentToUpdate)
        }
    }

}