package com.myprojects.scanwisp.domain.use_case

import android.content.Context
import com.myprojects.scanwisp.data.local.model.PageEntity
import com.myprojects.scanwisp.domain.model.ExportFormat
import com.myprojects.scanwisp.domain.model.PdfExportProfile
import com.myprojects.scanwisp.domain.repository.DocumentRepository
import com.myprojects.scanwisp.domain.repository.SettingsRepository
import com.myprojects.scanwisp.utils.JpegExportService
import com.myprojects.scanwisp.utils.PdfExportService
import com.myprojects.scanwisp.utils.SafeNamePolicy
import com.myprojects.scanwisp.utils.ZipExportService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class ExportDocumentUseCaseTest {

    private lateinit var mockDocumentRepository: DocumentRepository
    private lateinit var mockSettingsRepository: SettingsRepository
    private lateinit var mockPdfExportService: PdfExportService
    private lateinit var mockZipExportService: ZipExportService
    private lateinit var mockJpegExportService: JpegExportService
    private lateinit var useCase: ExportDocumentUseCase
    private lateinit var mockSafeNamePolicy: SafeNamePolicy

    private val fakeFile = mockk<File>()
    private val page1 = PageEntity("p1", "d1", 1, "", "/path/to/image1.jpg", "", 0L)
    private val page2 = PageEntity("p2", "d1", 2, "", "/path/to/image2.jpg", "", 1L)

    @Before
    fun setUp() {
        mockDocumentRepository = mockk(relaxUnitFun = true)
        mockSettingsRepository = mockk(relaxUnitFun = true)
        mockPdfExportService = mockk(relaxUnitFun = true)
        mockZipExportService = mockk(relaxUnitFun = true)
        mockJpegExportService = mockk(relaxUnitFun = true)
        mockSafeNamePolicy = mockk(relaxed = true)

        coEvery { mockPdfExportService.exportToPdf(any(), any(), any(), any()) } returns fakeFile
        coEvery { mockZipExportService.createZipArchive(any(), any()) } returns fakeFile
        coEvery { mockJpegExportService.copyToCache(any(), any()) } returns fakeFile

        every { mockSafeNamePolicy.exportBaseNameFromTitle(any(), any()) } returns "SanitizedName"

        val mockContext = mockk<Context>(relaxed = true) {
            every { packageName } returns "com.myprojects.scanwisp"
        }

        useCase = ExportDocumentUseCase(
            context = mockContext,
            documentRepository = mockDocumentRepository,
            settingsRepository = mockSettingsRepository,
            pdfExportService = mockPdfExportService,
            zipExportService = mockZipExportService,
            jpegExportService = mockJpegExportService
        )
    }

    @Test
    fun `invoke with PDF format calls PdfExportService`() = runTest {
        val pageIds = listOf("p1")
        coEvery { mockDocumentRepository.getPagesByIds(pageIds) } returns listOf(page1)
        every { mockSettingsRepository.pdfExportProfile } returns MutableStateFlow(PdfExportProfile.BALANCED)
        every { mockSettingsRepository.fitToA4 } returns MutableStateFlow(true)

        useCase(pageIds, "Test PDF", ExportFormat.PDF)

        // ИСПРАВЛЕНИЕ: Используем позиционные аргументы
        coVerify {
            mockPdfExportService.exportToPdf(
                listOf("/path/to/image1.jpg"),
                any(),
                PdfExportProfile.BALANCED,
                true
            )
        }
    }

    @Test
    fun `invoke with ZIP format calls ZipExportService`() = runTest {
        val pageIds = listOf("p1", "p2")
        coEvery { mockDocumentRepository.getPagesByIds(pageIds) } returns listOf(page1, page2)

        useCase(pageIds, "Test ZIP", ExportFormat.ZIP)

        // ИСПРАВЛЕНИЕ: Используем позиционные аргументы
        coVerify {
            mockZipExportService.createZipArchive(
                listOf("/path/to/image1.jpg", "/path/to/image2.jpg"),
                any()
            )
        }
    }

    @Test
    fun `invoke with JPEG format for single page calls JpegExportService`() = runTest {
        val pageIds = listOf("p1")
        coEvery { mockDocumentRepository.getPagesByIds(pageIds) } returns listOf(page1)

        useCase(pageIds, "Test JPEG", ExportFormat.JPEG)

        // ИСПРАВЛЕНИЕ: Используем позиционные аргументы
        coVerify {
            mockJpegExportService.copyToCache(
                "/path/to/image1.jpg",
                any()
            )
        }
    }

    @Test
    fun `invoke with JPEG format for multiple pages returns null and does not call service`() =
        runTest {
            val pageIds = listOf("p1", "p2")
            coEvery { mockDocumentRepository.getPagesByIds(pageIds) } returns listOf(page1, page2)

            val result = useCase(pageIds, "Test JPEG", ExportFormat.JPEG)

            assertNull(result)
            coVerify(exactly = 0) { mockJpegExportService.copyToCache(any(), any()) }
        }

    @Test
    fun `invoke with empty pageIds returns null`() = runTest {
        val result = useCase(emptyList(), "Test Empty", ExportFormat.PDF)

        assertNull(result)
        coVerify(exactly = 0) { mockDocumentRepository.getPagesByIds(any()) }
    }
}