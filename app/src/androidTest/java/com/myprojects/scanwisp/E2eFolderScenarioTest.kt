package com.myprojects.scanwisp

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.myprojects.scanwisp.data.local.DocumentDao
import com.myprojects.scanwisp.data.local.model.DocumentEntity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class E2eFolderScenarioTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var dao: DocumentDao

    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        hiltRule.inject()

        composeTestRule.setContent {
            navController = rememberNavController()
            AppNavHost(
                navController = navController,
                windowSizeClass = WindowWidthSizeClass.Compact,
                onboardingCheckState = OnboardingCheckState.Completed
            )
        }
    }

    @Test
    fun folderCreationAndDocumentMove_fullCycle() = runTest {
        // --- ЧАСТЬ 1: Подготовка и создание папки ---

        // 1. Arrange: Создаем документ, который будем перемещать
        val docToMove = DocumentEntity(
            id = "doc-move",
            title = "Document to Move",
            creationTimestamp = 1L,
            coverImagePath = "",
            folderId = null
        )
        dao.insertDocument(docToMove)
        composeTestRule.waitForIdle()

        // 2. Act: Переходим на экран папок
        val foldersNavDescription = composeTestRule.activity.getString(R.string.bottom_nav_folders)
        composeTestRule.onNodeWithText(foldersNavDescription).performClick()
        composeTestRule.waitForIdle()

        // 3. Act: Нажимаем FAB для создания папки
        val createFolderFabDesc = composeTestRule.activity.getString(R.string.fab_cd_create_folder)
        composeTestRule.onNodeWithContentDescription(createFolderFabDesc).performClick()

        // 4. Act: Вводим имя и подтверждаем
        val folderName = "My Test Folder"
        val folderNameLabel =
            composeTestRule.activity.getString(R.string.dialog_create_folder_label)
        composeTestRule.onNodeWithText(folderNameLabel).performTextInput(folderName)

        val createButtonText = composeTestRule.activity.getString(R.string.action_create)
        composeTestRule.onNodeWithText(createButtonText).performClick()

        // 5. Assert: Проверяем, что папка появилась в списке
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(folderName).assertIsDisplayed()

        // --- ЧАСТЬ 2: Перемещение документа ---

        // 6. Act: Возвращаемся на главный экран
        val homeNavDescription = composeTestRule.activity.getString(R.string.bottom_nav_home)
        composeTestRule.onNodeWithText(homeNavDescription).performClick()
        composeTestRule.waitForIdle()

        // 7. Act: Выбираем документ и нажимаем "Переместить"
        composeTestRule.onNodeWithText("Document to Move").performTouchInput { longClick() }

        val moveButtonDesc = composeTestRule.activity.getString(R.string.home_selection_bar_cd_move)
        composeTestRule.onNodeWithContentDescription(moveButtonDesc).performClick()

        // 8. Act: Выбираем нашу папку в диалоге
        composeTestRule.onNodeWithText(folderName).performClick()

        // 9. Assert: Проверяем, что документ исчез с главного экрана
        composeTestRule.waitForIdle()
        val emptyStateText =
            composeTestRule.activity.getString(R.string.empty_state_title_no_documents)
        composeTestRule.onNodeWithText(emptyStateText).assertIsDisplayed()

        // --- ЧАСТЬ 3: Финальная проверка ---

        // 10. Act: Снова переходим на экран папок
        composeTestRule.onNodeWithText(foldersNavDescription).performClick()
        composeTestRule.waitForIdle()

        // 11. Act: Заходим в созданную папку
        composeTestRule.onNodeWithText(folderName).performClick()

        // 12. Assert: Проверяем, что мы на экране папки и документ там
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(folderName)
            .assertIsDisplayed() // Заголовок экрана теперь - имя папки
        composeTestRule.onNodeWithText("Document to Move").assertIsDisplayed()
    }
}