package com.myprojects.scanwisp.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myprojects.scanwisp.data.local.AppDatabase
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.utils.ImageProcessor
import com.myprojects.scanwisp.utils.ProcessedImagePaths
import com.myprojects.scanwisp.utils.SafeNamePolicy
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.Assert.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DocumentRepositoryImplTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DocumentDao
    private lateinit var repository: DocumentRepository
    private lateinit var testContext: Context
    private lateinit var mockImageProcessor: ImageProcessor

    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        testContext = ApplicationProvider.getApplicationContext()
        // Создаем временную папку для тестовых файлов
        testDirectory = testContext.cacheDir.resolve("test_files").apply { mkdirs() }

        db = Room.inMemoryDatabaseBuilder(testContext, AppDatabase::class.java)
            .allowMainThreadQueries() // В тестах это допустимо
            .build()
        dao = db.documentDao()

        // Мокируем ImageProcessor, чтобы не зависеть от реальной обработки изображений
        mockImageProcessor = mockk()

        repository = DocumentRepositoryImpl(
            dao = dao,
            context = testContext,
            safeNamePolicy = SafeNamePolicy(testContext),
            imageProcessor = mockImageProcessor
        )
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        db.close()
        // Очищаем тестовую папку после каждого теста
        testDirectory.deleteRecursively()
    }

    @Test
    fun createDocument_createsDbEntriesAndFiles() = runTest {
        // Arrange
        val fakeUri = Uri.parse("content://fake/image1")
        val processedPath = File(testDirectory, "processed.jpg").absolutePath
        val thumbPath = File(testDirectory, "thumb.jpg").absolutePath

        // Создаем фейковые файлы, чтобы тест мог проверить их существование
        File(processedPath).createNewFile()
        File(thumbPath).createNewFile()

        coEvery { mockImageProcessor.processImageForStorage(fakeUri) } returns ProcessedImagePaths(
            processedImagePath = processedPath,
            thumbnailPath = thumbPath
        )

        // Act
        repository.createDocument("Test Doc", listOf(fakeUri), null)

        // Assert
        val documents = dao.getDocumentRows(null, "", "DATE", "DESC").first()
        assertEquals(1, documents.size)
        assertEquals("Test Doc", documents[0].title)

        val docId = documents[0].id
        val pages = dao.getAllPagesForDocument(docId)
        assertEquals(1, pages.size)
        assertEquals(processedPath, pages[0].processedImagePath)
        assertEquals(thumbPath, pages[0].thumbnailPath)

        // Проверяем, что файлы все еще существуют (т.е. не были случайно удалены)
        assertTrue(File(processedPath).exists())
        assertTrue(File(thumbPath).exists())
    }

    @Test
    fun deletePages_deletesDbEntriesAndFiles() = runTest {
        // Arrange: Сначала создаем документ с двумя страницами
        val processedPath1 = File(testDirectory, "p1.jpg").apply { createNewFile() }.absolutePath
        val thumbPath1 = File(testDirectory, "t1.jpg").apply { createNewFile() }.absolutePath
        val processedPath2 = File(testDirectory, "p2.jpg").apply { createNewFile() }.absolutePath
        val thumbPath2 = File(testDirectory, "t2.jpg").apply { createNewFile() }.absolutePath

        coEvery { mockImageProcessor.processImageForStorage(any()) } returnsMany listOf(
            ProcessedImagePaths(processedPath1, thumbPath1),
            ProcessedImagePaths(processedPath2, thumbPath2)
        )
        repository.createDocument("Doc with 2 pages", listOf(Uri.EMPTY, Uri.EMPTY), null)
        val docId = dao.getDocumentRows(null, "", "DATE", "DESC").first().first().id
        val pages = dao.getAllPagesForDocument(docId)
        val pageIdToDelete = pages.first().id

        assertTrue("Файл страницы 1 должен существовать до удаления", File(processedPath1).exists())

        // Act
        repository.deletePages(listOf(pageIdToDelete))

        // Assert
        val pagesAfterDelete = dao.getAllPagesForDocument(docId)
        assertEquals("Должна остаться одна страница", 1, pagesAfterDelete.size)
        assertEquals(
            pages.last().id,
            pagesAfterDelete.first().id
        ) // Проверяем, что осталась вторая страница

        // Самая важная проверка: файлы должны быть удалены
        assertFalse(
            "Файл обработанного изображения должен быть удален",
            File(processedPath1).exists()
        )
        assertFalse("Файл превью должен быть удален", File(thumbPath1).exists())
        assertTrue("Файлы второй страницы должны остаться", File(processedPath2).exists())
    }

    @Test
    fun deleteDocumentsPermanently_deletesEverything() = runTest {
        // Arrange
        val processedPath = File(testDirectory, "p.jpg").apply { createNewFile() }.absolutePath
        val thumbPath = File(testDirectory, "t.jpg").apply { createNewFile() }.absolutePath
        coEvery { mockImageProcessor.processImageForStorage(any()) } returns ProcessedImagePaths(
            processedPath,
            thumbPath
        )

        repository.createDocument("Doc to be deleted", listOf(Uri.EMPTY), null)
        val docId = dao.getDocumentRows(null, "", "DATE", "DESC").first().first().id

        // Act
        repository.deleteDocumentsPermanently(listOf(docId))

        // Assert
        val docsAfterDelete = dao.getDocumentRows(null, "", "DATE", "DESC").first()
        assertTrue("Список документов должен быть пуст", docsAfterDelete.isEmpty())

        val pagesAfterDelete = dao.getAllPagesForDocument(docId)
        assertTrue(
            "Список страниц для этого документа должен быть пуст",
            pagesAfterDelete.isEmpty()
        )

        assertFalse(
            "Файл обработанного изображения должен быть удален",
            File(processedPath).exists()
        )
        assertFalse("Файл превью должен быть удален", File(thumbPath).exists())
    }

    @Test
    fun mergeDocuments_combinesPagesAndDeletesOldDocs() = runTest {
        // Arrange: Создаем два документа, каждый с одной страницей
        val doc1Id = "doc-merge-1"
        val doc2Id = "doc-merge-2"

        coEvery { mockImageProcessor.processImageForStorage(any()) } returnsMany listOf(
            ProcessedImagePaths("p1", "t1"),
            ProcessedImagePaths("p2", "t2")
        )

        dao.insertDocument(
            com.myprojects.scanwisp.data.local.model.DocumentEntity(
                doc1Id,
                "Doc 1",
                100L,
                "t1",
                null
            )
        )
        // ИСПРАВЛЕНИЕ: Используем insertPages с одним элементом в списке
        dao.insertPages(
            listOf(
                com.myprojects.scanwisp.data.local.model.PageEntity(
                    "page1",
                    doc1Id,
                    1,
                    "s1",
                    "p1",
                    "t1",
                    0L
                )
            )
        )

        dao.insertDocument(
            com.myprojects.scanwisp.data.local.model.DocumentEntity(
                doc2Id,
                "Doc 2",
                200L,
                "t2",
                null
            )
        )
        // ИСПРАВЛЕНИЕ: Используем insertPages с одним элементом в списке
        dao.insertPages(
            listOf(
                com.myprojects.scanwisp.data.local.model.PageEntity(
                    "page2",
                    doc2Id,
                    1,
                    "s2",
                    "p2",
                    "t2",
                    0L
                )
            )
        )
        // Act
        repository.mergeDocuments(listOf(doc1Id, doc2Id), "Merged Doc", null)

        // Assert
        val allDocs = dao.getDocumentRows(null, "", "DATE", "DESC").first()
        assertEquals("Должен остаться только один (новый) документ", 1, allDocs.size)

        val newDoc = allDocs.first()
        assertEquals("Merged Doc", newDoc.title)

        val newPages = dao.getAllPagesForDocument(newDoc.id)
        assertEquals("В новом документе должно быть 2 страницы", 2, newPages.size)

        // Проверяем, что ID страниц новые, так как они были скопированы
        assertNotEquals("page1", newPages[0].id)
        assertNotEquals("page2", newPages[1].id)
    }

    @Test
    fun splitPagesIntoNewDocuments_createsNewDocsAndUpdatesOriginal() = runTest {
        // Arrange: Создаем один документ с тремя страницами
        val originalDocId = "doc-split-original"
        dao.insertDocument(
            com.myprojects.scanwisp.data.local.model.DocumentEntity(
                originalDocId,
                "Original",
                100L,
                "t1",
                null
            )
        )
        val page1 = com.myprojects.scanwisp.data.local.model.PageEntity(
            "p1",
            originalDocId,
            1,
            "s1",
            "p1",
            "t1",
            0L
        )
        val page2 = com.myprojects.scanwisp.data.local.model.PageEntity(
            "p2",
            originalDocId,
            2,
            "s2",
            "p2",
            "t2",
            1L
        )
        val page3 = com.myprojects.scanwisp.data.local.model.PageEntity(
            "p3",
            originalDocId,
            3,
            "s3",
            "p3",
            "t3",
            2L
        )
        dao.insertPages(listOf(page1, page2, page3))

        // Act: Отделяем две страницы (p2 и p3) в новые документы
        repository.splitPagesIntoNewDocuments(originalDocId, listOf("p2", "p3"), "Original", null)

        // Assert
        val allDocs = dao.getDocumentRows(null, "", "DATE", "DESC").first()
        // Должно стать 3 документа: оригинальный (с 1 страницей) + 2 новых (каждый с 1 страницей)
        assertEquals("Всего должно стать 3 документа", 3, allDocs.size)

        // Проверяем оригинальный документ
        val originalDocAfterSplit = dao.getDocumentById(originalDocId)
        assertNotNull(originalDocAfterSplit)
        val originalPagesAfterSplit = dao.getAllPagesForDocument(originalDocId)
        assertEquals(
            "В оригинальном документе должна остаться 1 страница",
            1,
            originalPagesAfterSplit.size
        )
        assertEquals("p1", originalPagesAfterSplit.first().id)

        // Проверяем, что создались новые документы, содержащие страницы p2 и p3
        val newDocForP2 = allDocs.find { it.title.contains("(Часть 1)") }
        val newDocForP3 = allDocs.find { it.title.contains("(Часть 2)") }
        assertNotNull(newDocForP2)
        assertNotNull(newDocForP3)

        val pageP2AfterSplit = dao.getPageById("p2").first()
        val pageP3AfterSplit = dao.getPageById("p3").first()

        assertEquals(newDocForP2!!.id, pageP2AfterSplit?.documentOwnerId)
        assertEquals(newDocForP3!!.id, pageP3AfterSplit?.documentOwnerId)
    }

    @Test
    fun splitPagesIntoNewDocuments_deletesOriginalIfAllPagesAreSplit() = runTest {
        // Arrange: Создаем документ с двумя страницами
        val originalDocId = "doc-split-all"
        dao.insertDocument(
            com.myprojects.scanwisp.data.local.model.DocumentEntity(
                originalDocId,
                "To Be Deleted",
                100L,
                "t1",
                null
            )
        )
        val page1 = com.myprojects.scanwisp.data.local.model.PageEntity(
            "p1",
            originalDocId,
            1,
            "s1",
            "p1",
            "t1",
            0L
        )
        val page2 = com.myprojects.scanwisp.data.local.model.PageEntity(
            "p2",
            originalDocId,
            2,
            "s2",
            "p2",
            "t2",
            1L
        )
        dao.insertPages(listOf(page1, page2))

        // Act: Отделяем ВСЕ страницы
        repository.splitPagesIntoNewDocuments(
            originalDocId,
            listOf("p1", "p2"),
            "To Be Deleted",
            null
        )

        // Assert
        val originalDocAfterSplit = dao.getDocumentById(originalDocId)
        assertNull("Оригинальный документ должен быть удален", originalDocAfterSplit)

        val allDocs = dao.getDocumentRows(null, "", "DATE", "DESC").first()
        assertEquals("Должно остаться только 2 новых документа", 2, allDocs.size)
    }

    @Test
    fun addPagesToDocument_addsNewPagesAndDbEntries() = runTest {
        // Arrange: Создаем документ с одной страницей
        val fakeUri1 = Uri.parse("content://fake/image1")
        val processedPath1 = File(testDirectory, "p1.jpg").apply { createNewFile() }.absolutePath
        val thumbPath1 = File(testDirectory, "t1.jpg").apply { createNewFile() }.absolutePath
        coEvery { mockImageProcessor.processImageForStorage(fakeUri1) } returns ProcessedImagePaths(
            processedPath1,
            thumbPath1
        )

        repository.createDocument("Original Doc", listOf(fakeUri1), null)
        val docId = dao.getDocumentRows(null, "", "DATE", "DESC").first().first().id

        // Arrange 2: Готовим данные для новой страницы
        val fakeUri2 = Uri.parse("content://fake/image2")
        val processedPath2 = File(testDirectory, "p2.jpg").apply { createNewFile() }.absolutePath
        val thumbPath2 = File(testDirectory, "t2.jpg").apply { createNewFile() }.absolutePath
        coEvery { mockImageProcessor.processImageForStorage(fakeUri2) } returns ProcessedImagePaths(
            processedPath2,
            thumbPath2
        )

        // Act
        repository.addPagesToDocument(docId, listOf(fakeUri2))

        // Assert
        val pages = dao.getAllPagesForDocument(docId)
        assertEquals("В документе должно быть 2 страницы", 2, pages.size)

        val newPage = pages.find { it.processedImagePath == processedPath2 }
        assertNotNull("Новая страница должна была быть добавлена", newPage)
        assertEquals("Позиция новой страницы должна быть 1", 1L, newPage?.position)
    }

    @Test
    fun deletePages_updatesCoverImage_whenCoverIsDeleted() = runTest {
        // Arrange: Документ с 2 страницами, первая - обложка
        val page1 = PageEntity("p1", "doc1", 1, "", "proc1.jpg", "thumb1.jpg", 0L)
        val page2 = PageEntity("p2", "doc1", 2, "", "proc2.jpg", "thumb2.jpg", 1L)
        val doc = DocumentEntity("doc1", "Doc with Cover", 1L, page1.thumbnailPath, null)
        dao.insertDocument(doc)
        dao.insertPages(listOf(page1, page2))

        File(testDirectory, "thumb1.jpg").createNewFile()
        File(testDirectory, "proc1.jpg").createNewFile()

        // Act: Удаляем первую страницу (которая является обложкой)
        repository.deletePages(listOf("p1"))

        // Assert
        val updatedDoc = dao.getDocumentById("doc1")
        assertNotNull(updatedDoc)
        // Обложка должна была переключиться на вторую страницу
        assertEquals(
            "Обложка должна была обновиться на thumb2.jpg",
            page2.thumbnailPath,
            updatedDoc?.coverImagePath
        )
    }

    @Test
    fun replacePageImage_deletesOldFiles_andUpdatesDbEntry() = runTest {
        // Arrange: Создаем документ с одной страницей
        val oldProcessedPath =
            File(testDirectory, "old_p.jpg").apply { createNewFile() }.absolutePath
        val oldThumbPath = File(testDirectory, "old_t.jpg").apply { createNewFile() }.absolutePath

        val doc = DocumentEntity("doc1", "Doc to Replace", 1L, oldThumbPath, null)
        val page = PageEntity("p1", doc.id, 1, "old_uri", oldProcessedPath, oldThumbPath, 0L)
        dao.insertDocument(doc)
        dao.insertPages(listOf(page))

        // Arrange 2: Готовим данные для "нового" изображения
        val newUri = Uri.parse("content://fake/new_image")
        val newProcessedPath = File(testDirectory, "new_p.jpg").absolutePath
        val newThumbPath = File(testDirectory, "new_t.jpg").absolutePath
        coEvery { mockImageProcessor.processImageForStorage(newUri) } returns ProcessedImagePaths(
            newProcessedPath,
            newThumbPath
        )

        // Act
        repository.replacePageImage("p1", newUri)

        // Assert
        val updatedPage = dao.getPageById("p1").first()
        assertNotNull(updatedPage)
        assertEquals(
            "Путь к обработанному изображению должен обновиться",
            newProcessedPath,
            updatedPage?.processedImagePath
        )
        assertEquals("Путь к превью должен обновиться", newThumbPath, updatedPage?.thumbnailPath)

        // Проверяем, что обложка документа тоже обновилась
        val updatedDoc = dao.getDocumentById("doc1")
        assertEquals(
            "Обложка документа должна была обновиться",
            newThumbPath,
            updatedDoc?.coverImagePath
        )

        // Проверяем, что старые файлы были удалены
        assertFalse(
            "Старый файл обработанного изображения должен быть удален",
            File(oldProcessedPath).exists()
        )
        assertFalse("Старый файл превью должен быть удален", File(oldThumbPath).exists())
    }
}
