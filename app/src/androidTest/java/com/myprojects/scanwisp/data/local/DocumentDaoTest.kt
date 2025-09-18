package com.myprojects.scanwisp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.FolderEntity
import com.myprojects.scanwisp.data.local.model.PageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class) // Указываем, что это инструментальный тест
class DocumentDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DocumentDao

    // @Before - этот метод выполняется ПЕРЕД каждым тестом
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Создаем базу данных в оперативной памяти
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
        dao = db.documentDao()
    }

    // @After - этот метод выполняется ПОСЛЕ каждого теста
    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun insertFolderAndGetById_returnsSameFolder() = runTest { // runTest для suspend-функций
        // Arrange (Подготовка)
        val folder = FolderEntity(id = "folder1", name = "Test Folder", creationTimestamp = 1L)
        dao.insertFolder(folder)

        // Act (Действие)
        val loaded = dao.getFolderById("folder1")

        // Assert (Проверка)
        assertEquals(folder, loaded)
    }

    @Test
    @Throws(Exception::class)
    fun insertDocumentAndPages_andGetRelation_returnsCorrectData() = runTest {
        // Arrange
        val document = DocumentEntity("doc1", "My Doc", 1L, "/cover.jpg", null)
        val page1 = PageEntity("page1", "doc1", 1, "/src1.jpg", "/proc1.jpg", "/thumb1.jpg", 0L)
        val page2 = PageEntity("page2", "doc1", 2, "/src2.jpg", "/proc2.jpg", "/thumb2.jpg", 1L)
        dao.insertDocumentAndPages(document, listOf(page1, page2))

        // Act
        // .first() получает первое значение из Flow и завершает его, идеально для тестов
        val documentWithPages = dao.getDocumentWithPagesById("doc1").first()

        // Assert
        assertEquals("doc1", documentWithPages?.document?.id)
        assertEquals(2, documentWithPages?.pages?.size)
        assertEquals("page1", documentWithPages?.pages?.get(0)?.id)
        assertEquals("page2", documentWithPages?.pages?.get(1)?.id)
    }

    @Test
    @Throws(Exception::class)
    fun deleteDocument_cascadesToDeletePages() = runTest {
        // Arrange
        val document = DocumentEntity("doc1", "My Doc", 1L, "/cover.jpg", null)
        val page1 = PageEntity("page1", "doc1", 1, "/src1.jpg", "/proc1.jpg", "/thumb1.jpg", 0L)
        dao.insertDocumentAndPages(document, listOf(page1))

        // Act
        dao.deleteDocumentById("doc1")

        // Assert
        val loadedDoc = dao.getDocumentById("doc1")
        val loadedPage = dao.getPageById("page1").first()

        // ИСПРАВЛЕНИЕ: Текст сообщения ассерта
        assertNull("Документ должен был быть удален", loadedDoc)
        assertNull("Связанная страница должна была удалиться каскадно", loadedPage)
    }

    @Test
    @Throws(Exception::class)
    fun getFoldersWithDocumentCount_returnsCorrectCount() = runTest {
        // Arrange
        val folder1 = FolderEntity("folder1", "Work", 1L)
        val folder2 = FolderEntity("folder2", "Home", 2L)
        dao.insertFolder(folder1)
        dao.insertFolder(folder2)

        dao.insertDocument(DocumentEntity("doc1", "Doc 1", 3L, "", "folder1"))
        dao.insertDocument(DocumentEntity("doc2", "Doc 2", 4L, "", "folder1"))
        dao.insertDocument(DocumentEntity("doc3", "Doc 3", 5L, "", "folder2"))
        // Один документ без папки (orphan)
        dao.insertDocument(DocumentEntity("doc4", "Doc 4", 6L, "", null))

        // Act
        val foldersWithCount = dao.getAllFoldersWithDocumentCount().first()

        // Assert
        val workFolder = foldersWithCount.find { it.folder.id == "folder1" }
        val homeFolder = foldersWithCount.find { it.folder.id == "folder2" }

        assertEquals(2, workFolder?.documentCount)
        assertEquals(1, homeFolder?.documentCount)
    }

    @Test
    fun getDocumentRows_withSearchQuery_returnsMatchingDocuments() = runTest {
        // Arrange
        val folder1 = FolderEntity("folder1", "Receipts", 1L)
        dao.insertFolder(folder1)
        // FTS требует, чтобы контент был вставлен в основную таблицу
        dao.insertDocument(DocumentEntity("doc1", "Receipt from Store", 2L, "", "folder1"))
        dao.insertDocument(DocumentEntity("doc2", "Gas Bill", 3L, "", "folder1"))
        dao.insertDocument(DocumentEntity("doc3", "Another Store receipt", 4L, "", "folder1"))

        // Act
        val searchResultStore = dao.getDocumentRows("folder1", "\"Store\"*", "DATE", "DESC").first()
        val searchResultBill = dao.getDocumentRows("folder1", "\"Bill\"*", "DATE", "DESC").first()
        val searchResultEmpty =
            dao.getDocumentRows("folder1", "\"NonExistent\"*", "DATE", "DESC").first()
        val searchResultAll =
            dao.getDocumentRows("folder1", "", "DATE", "DESC").first() // Пустой запрос

        // Assert
        assertEquals(2, searchResultStore.size)
        assertTrue(searchResultStore.any { it.title == "Receipt from Store" })
        assertTrue(searchResultStore.any { it.title == "Another Store receipt" })

        assertEquals(1, searchResultBill.size)
        assertEquals("Gas Bill", searchResultBill[0].title)

        assertEquals(0, searchResultEmpty.size)
        assertEquals(3, searchResultAll.size)
    }

    // --- НОВЫЕ ТЕСТЫ ---

    @Test
    fun softDeleteAndUndo_worksCorrectly() = runTest {
        // Arrange
        val doc = DocumentEntity("doc1", "Test Doc", 1L, "", null)
        dao.insertDocument(doc)

        // Act 1: Помечаем на удаление
        val deletionTime = System.currentTimeMillis()
        dao.markDocumentsForDeletion(listOf("doc1"), deletionTime)

        // Assert 1
        val markedDoc = dao.getDocumentById("doc1")
        assertNotNull("Документ не должен быть null после мягкого удаления", markedDoc)
        assertEquals(deletionTime, markedDoc?.deletionTimestamp)

        // Act 2: Проверяем, что документ попадает в выборку для GC
        val pendingDocs = dao.getDocumentsPendingDeletion(deletionTime + 1)
        assertEquals(1, pendingDocs.size)
        assertEquals("doc1", pendingDocs[0].id)

        // Act 3: Отменяем удаление
        dao.unmarkDocumentsForDeletion(listOf("doc1"))

        // Assert 3
        val unmarkedDoc = dao.getDocumentById("doc1")
        assertNull("Timestamp удаления должен быть сброшен", unmarkedDoc?.deletionTimestamp)
    }

    @Test
    fun moveDocumentsToFolder_updatesFolderId() = runTest {
        // Arrange
        val folder = FolderEntity("folder1", "New Home", 1L)
        val doc1 = DocumentEntity("doc1", "Doc 1", 2L, "", null)
        val doc2 = DocumentEntity("doc2", "Doc 2", 3L, "", null)
        dao.insertFolder(folder)
        dao.insertDocument(doc1)
        dao.insertDocument(doc2)

        // Act: Перемещаем в папку
        dao.moveDocumentsToFolder(listOf("doc1", "doc2"), "folder1")

        // Assert
        assertEquals("folder1", dao.getDocumentById("doc1")?.folderId)
        assertEquals("folder1", dao.getDocumentById("doc2")?.folderId)

        // Act: Перемещаем обратно в корень
        dao.moveDocumentsToFolder(listOf("doc1"), null)
        assertNull(dao.getDocumentById("doc1")?.folderId)
    }

    @Test
    fun getDocumentRows_forNullFolderId_returnsOnlyOrphanDocuments() = runTest {
        // Arrange
        val folder = FolderEntity("folder1", "Folder", 1L)
        val docWithFolder = DocumentEntity("doc1", "In Folder", 2L, "", "folder1")
        val docWithoutFolder = DocumentEntity("doc2", "Orphan Doc", 3L, "", null)
        dao.insertFolder(folder)
        dao.insertDocument(docWithFolder)
        dao.insertDocument(docWithoutFolder)

        // Act
        val orphans = dao.getDocumentRows(null, "", "DATE", "DESC").first()

        // Assert
        assertEquals(1, orphans.size)
        assertEquals("doc2", orphans[0].id)
    }

    @Test
    fun deletePagesAndHandleCoverUpdate_updatesCoverWhenNeeded() = runTest {
        // Arrange
        val doc = DocumentEntity("doc1", "My Doc", 1L, "/thumb1.jpg", null)
        val page1 = PageEntity("page1", "doc1", 1, "", "", "/thumb1.jpg", 0L)
        val page2 = PageEntity("page2", "doc1", 2, "", "", "/thumb2.jpg", 1L)
        val page3 = PageEntity("page3", "doc1", 3, "", "", "/thumb3.jpg", 2L)
        dao.insertDocumentAndPages(doc, listOf(page1, page2, page3))

        // Act 1: Удаляем текущую обложку (page1)
        val docToUpdate = doc.copy(coverImagePath = "/thumb2.jpg") // Новая обложка - page2
        dao.deletePagesAndHandleCoverUpdate(docToUpdate, listOf("page1"))

        // Assert 1
        val updatedDoc = dao.getDocumentById("doc1")
        val pages = dao.getAllPagesForDocument("doc1")
        assertEquals("/thumb2.jpg", updatedDoc?.coverImagePath)
        assertEquals(2, pages.size)

        // Act 2: Удаляем страницу, которая не является обложкой (page3)
        // Обложка не меняется, поэтому передаем тот же документ
        dao.deletePagesAndHandleCoverUpdate(updatedDoc!!, listOf("page3"))

        // Assert 2
        val finalDoc = dao.getDocumentById("doc1")
        assertEquals("/thumb2.jpg", finalDoc?.coverImagePath)
        assertEquals(1, dao.getAllPagesForDocument("doc1").size)
    }
}