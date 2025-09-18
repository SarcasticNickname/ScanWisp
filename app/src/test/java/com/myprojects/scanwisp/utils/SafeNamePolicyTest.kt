package com.myprojects.scanwisp.utils

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SafeNamePolicyTest {

    // Объявляем объект, который будем тестировать
    private lateinit var safeNamePolicy: SafeNamePolicy

    // @Before - этот метод будет выполняться перед каждым тестом (@Test)
    @Before
    fun setUp() {
        // SafeNamePolicy требует Context, но для этих тестов он не используется.
        // Поэтому мы создаем "пустышку" (мок) с помощью MockK.
        val mockContext = mockk<Context>(relaxed = true)
        safeNamePolicy = SafeNamePolicy(mockContext)
    }

    // Тест №1: Проверяем, что базовый, "чистый" текст не изменяется.
    @Test
    fun `sanitizeBase given clean string should return it unchanged`() {
        // Arrange (Подготовка)
        val input = "My Important Document"

        // Act (Действие)
        val result = safeNamePolicy.sanitizeBase(input)

        // Assert (Проверка)
        assertEquals(input, result)
    }

    // Тест №2: Проверяем удаление недопустимых символов.
    @Test
    fun `sanitizeBase given string with illegal characters should remove them`() {
        val input = "My<Receipt>:*?\"|/Invoice"
        val expected = "My Receipt Invoice"

        val result = safeNamePolicy.sanitizeBase(input)

        assertEquals(expected, result)
    }

    // Тест №3: Проверяем, что лишние пробелы "схлопываются" в один.
    @Test
    fun `sanitizeBase given string with multiple spaces should condense them`() {
        val input = "Document   with    extra spaces"
        val expected = "Document with extra spaces"

        val result = safeNamePolicy.sanitizeBase(input)

        assertEquals(expected, result)
    }

    // Тест №4: Проверяем обрезку пробелов и точек по краям.
    @Test
    fun `sanitizeBase given string with trailing spaces and dots should trim them`() {
        val input = "  .My Document.  "
        val expected = "My Document"

        val result = safeNamePolicy.sanitizeBase(input)

        assertEquals(expected, result)
    }

    // Тест №5: Проверяем, что зарезервированное имя Windows ("con") изменяется.
    @Test
    fun `sanitizeBase given reserved windows filename should append suffix`() {
        val input = "con"
        val expected = "con_file"

        val result = safeNamePolicy.sanitizeBase(input)

        assertEquals(expected, result)
    }

    // Тест №6: Проверяем, что имя документа по умолчанию имеет правильный формат.
    @Test
    fun `newDocumentTitle should create title with correct format`() {
        // Используем фиксированную дату, чтобы тест был предсказуемым
        val timestamp = 1757521463027L // Примерная дата из ваших логов
        val expectedDateFormat = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault())
        val expectedDateString = expectedDateFormat.format(Date(timestamp))
        val expected = "Scan $expectedDateString"

        val result = safeNamePolicy.newDocumentTitle(now = timestamp)

        assertEquals(expected, result)
        assertTrue(result.startsWith("Scan "))
    }
}