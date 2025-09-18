package com.myprojects.scanwisp.ads

import android.content.Context
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.consent.ConsentManager
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import com.myprojects.scanwisp.rules.MainDispatcherRule
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdPoolManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var mockContext: Context
    private lateinit var mockRemoteConfig: RemoteConfigRepository
    private lateinit var mockConsentManager: ConsentManager
    private lateinit var adPoolManager: AdPoolManager

    // Поток для управления состоянием согласия в тестах
    private val canRequestAdsFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockRemoteConfig = mockk(relaxed = true)
        mockConsentManager = mockk {
            every { canRequestAds } returns canRequestAdsFlow
        }

        // Мокируем статические вызовы AdMob SDK
        mockkStatic(MobileAds::class)
        every { MobileAds.initialize(any(), any()) } just Runs

        mockkStatic(AdLoader.Builder::class)
    }

    @Test
    fun `init does NOT fill pool if consent is not granted`() = runTest {
        // Arrange
        canRequestAdsFlow.value = false // Согласие не дано

        // Act
        adPoolManager = AdPoolManager(mockContext, mockRemoteConfig, mockConsentManager)
        runCurrent() // Запускаем корутины в init

        // Assert
        // Проверяем, что AdLoader.Builder никогда не был вызван
        verify(exactly = 0) { AdLoader.Builder(any(), any()) }
    }

    @Test
    fun `fillPool does NOT load ads if ads are disabled via remote config`() = runTest {
        // Arrange
        every { mockRemoteConfig.isNativeAdEnabled() } returns false // Реклама отключена
        canRequestAdsFlow.value = true // Но согласие дано

        // Act
        adPoolManager = AdPoolManager(mockContext, mockRemoteConfig, mockConsentManager)
        runCurrent()

        // Assert
        verify(exactly = 0) { AdLoader.Builder(any(), any()) }
    }

    @Test
    fun `init starts filling pool when consent is granted and ads are enabled`() = runTest {
        // Arrange
        every { mockRemoteConfig.isNativeAdEnabled() } returns true
        val mockAdLoader = mockk<AdLoader>(relaxed = true)
        val builderSlot = slot<AdLoader.Builder>()

        // Настраиваем мок для AdLoader.Builder
        every { AdLoader.Builder(any(), any()) } answers {
            mockk {
                every { forNativeAd(any()) } returns this
                every { withAdListener(any()) } returns this
                every { build() } returns mockAdLoader
                // Сохраняем экземпляр builder, чтобы проверить его позже, если нужно
                builderSlot.captured = this
            }
        }

        // Act
        adPoolManager = AdPoolManager(mockContext, mockRemoteConfig, mockConsentManager)
        // Сначала согласия нет
        runCurrent()
        verify(exactly = 0) { mockAdLoader.loadAds(any(), any()) }

        // Теперь даем согласие
        canRequestAdsFlow.value = true
        runCurrent() // Запускаем корутину, которая ждала согласия

        // Assert
        // Проверяем, что была попытка загрузить рекламу
        // POOL_CAPACITY = 3
        verify(exactly = 1) { mockAdLoader.loadAds(any(), 3) }
    }

    @Test
    fun `getAd returns null when pool is empty`() = runTest {
        // Arrange
        every { mockRemoteConfig.isNativeAdEnabled() } returns true
        canRequestAdsFlow.value = true
        adPoolManager = AdPoolManager(mockContext, mockRemoteConfig, mockConsentManager)
        runCurrent()

        // Act
        val ad = adPoolManager.getAd()

        // Assert
        assertNull(ad)
    }

    @Test
    fun `getAd returns an ad and triggers pool refill`() = runTest {
        // Arrange
        val mockAdLoader = mockk<AdLoader>(relaxed = true)
        // Слот для перехвата колбэка
        val nativeAdCallback = slot<NativeAd.OnNativeAdLoadedListener>()

        every { AdLoader.Builder(any(), any()) } answers {
            mockk {
                // Перехватываем колбэк, который будет вызван при "успешной загрузке"
                every { forNativeAd(capture(nativeAdCallback)) } returns this
                every { withAdListener(any()) } returns this
                every { build() } returns mockAdLoader
            }
        }

        every { mockRemoteConfig.isNativeAdEnabled() } returns true
        canRequestAdsFlow.value = true
        adPoolManager = AdPoolManager(mockContext, mockRemoteConfig, mockConsentManager)
        runCurrent() // Запускаем init и первую загрузку

        // Имитируем успешную загрузку одного объявления
        val fakeAd = mockk<NativeAd>(relaxed = true)
        nativeAdCallback.captured.onNativeAdLoaded(fakeAd)
        runCurrent()

        // Act
        val retrievedAd = adPoolManager.getAd()

        // Assert
        assertNotNull(retrievedAd)
        assertEquals(fakeAd, retrievedAd)

        // Проверяем, что была инициирована вторая загрузка для пополнения пула
        // Первый вызов был в init, второй - после getAd()
        verify(exactly = 2) { mockAdLoader.loadAds(any(), any()) }
    }
}