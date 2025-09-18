package com.myprojects.scanwisp.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class StorageServiceImplTest {

    private lateinit var context: Context
    private lateinit var service: StorageService
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = StorageServiceImpl(context)
        // Используем директорию кэша, так как она всегда доступна для записи
        testDir = context.cacheDir
    }

    @After
    fun tearDown() {
        // Очистка не требуется, так как мы не создаем файлы, а только проверяем место
    }

    @Test
    fun tryReserve_whenEnoughSpace_succeedsAndReturnsReservation() {
        // Arrange
        // Запрашиваем относительно небольшой объем, который точно должен быть доступен
        val requiredBytes = 10L * 1024 * 1024 // 10 MB

        // Act
        val reservation = service.tryReserve(requiredBytes, dir = testDir)

        // Assert
        assertNotNull("Резервирование должно быть успешным", reservation)
        assertEquals(
            "Размер резерва должен соответствовать запрошенному",
            requiredBytes,
            reservation?.bytes
        )

        // Очистка
        reservation?.close()
    }

    @Test
    fun tryReserve_whenNotEnoughSpace_failsAndReturnsNull() {
        // Arrange
        // Запрашиваем заведомо нереальный объем памяти
        val requiredBytes = Long.MAX_VALUE / 2

        // Act
        val reservation = service.tryReserve(requiredBytes, dir = testDir)

        // Assert
        assertNull("Резервирование не должно быть успешным при нехватке места", reservation)
    }

    @Test
    fun spaceReservation_whenClosed_releasesReservedBytes() = runTest {
        // Arrange
        val requiredBytes = 5L * 1024 * 1024 // 5 MB
        val availableBeforeReservation = service.getAvailableBytes(testDir)

        // Убедимся, что места достаточно для начала теста
        assertTrue(availableBeforeReservation > requiredBytes)

        // Act
        val reservation = service.tryReserve(requiredBytes, dir = testDir)
        assertNotNull(reservation) // Убедимся, что резервирование прошло успешно

        val availableDuringReservation = service.getAvailableBytes(testDir)

        // Закрываем резерв (это имитирует выход из блока .use { ... })
        reservation!!.close()

        val availableAfterReservation = service.getAvailableBytes(testDir)

        // Assert
        // Проверяем, что во время резерва доступное место уменьшилось
        val diffDuring = availableBeforeReservation - availableDuringReservation
        assertTrue(
            "Доступное место должно было уменьшиться примерно на $requiredBytes",
            diffDuring >= requiredBytes
        )

        // Проверяем, что после закрытия резерва место "вернулось"
        val finalDiff = abs(availableBeforeReservation - availableAfterReservation)
        // Допускаем небольшую погрешность, т.к. система могла использовать место
        assertTrue(
            "После закрытия резерва доступное место должно было вернуться к исходному",
            finalDiff < 1024 * 1024
        ) // меньше 1MB
    }
}