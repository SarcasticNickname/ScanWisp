package com.myprojects.scanwisp.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Этот тест генерирует Baseline Profile для приложения.
 * Он имитирует типичные действия пользователя: запуск, скроллинг и навигацию.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateAppStartupAndInteractionProfile() { // Переименовали для ясности
        rule.collect(
            packageName = "com.myprojects.scanwisp",
        ) {
            // 1. Сценарий "Холодный старт"
            pressHome()
            startActivityAndWait()

            // Ждем, пока на экране появится список документов
            device.wait(Until.hasObject(By.scrollable(true)), 10_000)
            val documentList = device.findObject(By.scrollable(true))

            // 2. Сценарий "Скроллинг списка"
            documentList?.fling(Direction.DOWN)
            device.waitForIdle()

            // 3. НОВЫЙ СЦЕНАРИЙ: Навигация на экран деталей и обратно
            // Находим первый кликабельный элемент в списке (первый документ)
            val firstDocument = documentList?.children?.firstOrNull { it.isClickable }
            if (firstDocument != null) {
                // Открываем экран деталей
                firstDocument.click()
                // Ждем, пока появится контент на экране деталей (например, список страниц)
                device.wait(Until.hasObject(By.scrollable(true)), 5_000)

                // Возвращаемся назад
                device.pressBack()
                // Ждем, пока снова появится главный экран
                device.wait(Until.hasObject(By.scrollable(true)), 5_000)
            }
        }
    }
}