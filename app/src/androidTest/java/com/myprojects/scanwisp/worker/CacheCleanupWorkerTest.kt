package com.myprojects.scanwisp.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CacheCleanupWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun doWork_deletesOldFiles_andKeepsNewFiles() = runBlocking {
        // Arrange (Подготовка)
        val pdfDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val oldFile = File.createTempFile("old_pdf_", ".pdf", pdfDir)
        val newFile = File.createTempFile("new_pdf_", ".pdf", pdfDir)

        // Устанавливаем время модификации: 10 дней назад для старого файла
        val tenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)
        oldFile.setLastModified(tenDaysAgo)

        assertTrue("Старый файл должен существовать перед тестом", oldFile.exists())
        assertTrue("Новый файл должен существовать перед тестом", newFile.exists())

        // Создаем и запускаем воркер
        val worker = TestListenableWorkerBuilder<CacheCleanupWorker>(context).build()

        // Act (Действие)
        val result = worker.doWork()

        // Assert (Проверка)
        assertTrue(
            "Результат работы воркера должен быть success",
            result is ListenableWorker.Result.Success
        )
        assertFalse("Старый файл должен был быть удален", oldFile.exists())
        assertTrue("Новый файл должен был остаться", newFile.exists())

        // Очистка
        newFile.delete()
        pdfDir.delete()
    }
}