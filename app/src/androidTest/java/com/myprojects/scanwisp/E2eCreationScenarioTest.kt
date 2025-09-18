package com.myprojects.scanwisp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.rule.IntentsRule
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myprojects.scanwisp.utils.ImageProcessor
import com.myprojects.scanwisp.utils.ProcessedImagePaths
import com.myprojects.scanwisp.utils.SafeNamePolicy
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@HiltAndroidTest
class E2eCreationScenarioTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule(order = 2)
    val intentsRule = IntentsRule()

    @BindValue
    @JvmField
    val mockImageProcessor: ImageProcessor = mockk(relaxed = true)

    @BindValue
    @JvmField
    val mockSafeNamePolicy: SafeNamePolicy = mockk(relaxed = true)

    // Определяем строковые константы, которые не являются публичными в SDK
    private val SCANNER_ACTION = "com.google.android.gms.vision.documentscanner.SCAN_DOCUMENT"
    private val SCANNER_RESULT_EXTRA = "com.google.android.gms.vision.documentscanner.RESULT"

    @Before
    fun setUp() {
        hiltRule.inject()

        coEvery { mockImageProcessor.processImageForStorage(any()) } coAnswers {
            val fakeFile = File(composeTestRule.activity.cacheDir, "fake_processed.jpg")
            fakeFile.createNewFile()
            ProcessedImagePaths(fakeFile.absolutePath, fakeFile.absolutePath)
        }

        every { mockSafeNamePolicy.newDocumentTitle(any()) } returns "New Scanned Document"
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun createDocument_fromScanner_showsNewDocumentOnHomeScreen() {
        // --- ЧАСТЬ 1: Подготовка и мокирование результата сканера ---

        val fakeScanUri = Uri.fromFile(File("fake.jpg"))
        val fakePage = mockk<GmsDocumentScanningResult.Page> {
            every { imageUri } returns fakeScanUri
        }
        val fakeScanResult = mockk<GmsDocumentScanningResult> {
            every { pages } returns listOf(fakePage)
            every { pdf } returns null
        }

        val resultIntent = Intent()
        // ИСПРАВЛЕНИЕ: Используем нашу строковую константу
        resultIntent.putExtra(SCANNER_RESULT_EXTRA, fakeScanResult)

        val intentResult = Instrumentation.ActivityResult(Activity.RESULT_OK, resultIntent)
        // ИСПРАВЛЕНИЕ: Используем нашу строковую константу
        Intents.intending(IntentMatchers.hasAction(SCANNER_ACTION))
            .respondWith(intentResult)

        // --- ЧАСТЬ 2: Выполнение сценария ---

        composeTestRule.setContent {
            AppNavHost(
                navController = rememberNavController(),
                windowSizeClass = WindowWidthSizeClass.Compact,
                onboardingCheckState = OnboardingCheckState.Completed
            )
        }

        val fabDescription = composeTestRule.activity.getString(R.string.fab_cd_scan_document)
        composeTestRule.onNodeWithContentDescription(fabDescription).performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("New Scanned Document").assertIsDisplayed()
    }
}