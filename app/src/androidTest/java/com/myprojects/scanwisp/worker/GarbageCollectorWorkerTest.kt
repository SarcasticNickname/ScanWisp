package com.myprojects.scanwisp.worker

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
class GarbageCollectorWorkerTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dao: DocumentDao // Hilt предоставит in-memory DAO из тестового модуля

    private lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()

        // Инициализируем WorkManager для тестов с Hilt
        val config = Configuration.Builder().setWorkerFactory(workerFactory).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun doWork_deletesOldSoftDeletedDocuments() = runBlocking {
        // Arrange
        val tenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)
        val oneDayAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)

        // Документ, который должен быть удален
        val oldDoc = DocumentEntity("doc1", "Old", 1L, "", null, deletionTimestamp = tenDaysAgo)
        // Документ, который должен остаться
        val newDoc = DocumentEntity("doc2", "New", 2L, "", null, deletionTimestamp = oneDayAgo)
        // Активный документ
        val activeDoc = DocumentEntity("doc3", "Active", 3L, "", null, deletionTimestamp = null)

        dao.insertDocument(oldDoc)
        dao.insertDocument(newDoc)
        dao.insertDocument(activeDoc)

        val worker = TestListenableWorkerBuilder<GarbageCollectorWorker>(context).build()

        // Act
        val result = worker.doWork()

        // Assert
        assertTrue(result is ListenableWorker.Result.Success)
        assertNull("Старый документ должен быть физически удален", dao.getDocumentById("doc1"))
        assertEquals("doc2", dao.getDocumentById("doc2")?.id)
        assertEquals("doc3", dao.getDocumentById("doc3")?.id)
    }
}