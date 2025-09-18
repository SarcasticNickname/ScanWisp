package com.myprojects.scanwisp.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.myprojects.scanwisp.consent.ConsentManager
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val POOL_CAPACITY = 3
private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

@Singleton
class AdPoolManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val consentManager: ConsentManager
) {
    private val adPool = ConcurrentLinkedQueue<NativeAd>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isAdLoading = AtomicBoolean(false)

    // НОВОЕ ПОЛЕ: Флаг для однократной инициализации SDK
    private val isMobileAdsInitialized = AtomicBoolean(false)

    init {
        scope.launch {
            // Ждем, пока consentManager не даст разрешение.
            consentManager.canRequestAds.filter { it }.first()

            //  Инициализируем SDK прямо здесь, в фоновом потоке,
            // как только получили согласие, но ДО первой загрузки рекламы.
            if (isMobileAdsInitialized.compareAndSet(false, true)) {
                // Запускаем инициализацию в главном потоке, как того требует SDK,
                // но сам вызов происходит с задержкой и не блокирует старт приложения.
                launch(Dispatchers.Main) {
                    MobileAds.initialize(context) {}
                    Timber.d("Mobile Ads SDK initialized on-demand.")
                }
            }

            Timber.d("Consent received. Initializing ad pool.")
            fillPool()
        }
    }

    fun getAd(): NativeAd? {
        val ad = adPool.poll()
        if (ad != null) {
            scope.launch {
                fillPool()
            }
        }
        return ad
    }

    private fun fillPool() {
        if (!consentManager.canRequestAds.value) {
            Timber.d("Consent not granted. Skipping pool fill.")
            return
        }

        if (!remoteConfigRepository.isNativeAdEnabled()) {
            Timber.d("Ads are disabled via Remote Config. Skipping pool fill.")
            return
        }

        if (!isAdLoading.compareAndSet(false, true)) {
            Timber.d( "Ad loading is already in progress.")
            return
        }

        val adsToLoad = POOL_CAPACITY - adPool.size
        if (adsToLoad <= 0) {
            Timber.d("Ad pool is already full.")
            isAdLoading.set(false)
            return
        }

        scope.launch {
            Timber.d( "Attempting to load $adsToLoad new ad(s).")

            val adLoader = AdLoader.Builder(context, AD_UNIT_ID)
                .forNativeAd { nativeAd ->
                    adPool.offer(nativeAd)
                    Timber.d( "Native ad loaded and added to pool. Pool size: ${adPool.size}")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Timber.d("Ad failed to load: ${loadAdError.message}")
                    }
                })
                .build()

            launch(Dispatchers.Main) {
                adLoader.loadAds(AdRequest.Builder().build(), adsToLoad)
            }

            delay(5000)
            isAdLoading.set(false)

            if (adPool.size < POOL_CAPACITY) {
                fillPool()
            }
        }
    }

    fun destroyAllAds() {
        while (adPool.isNotEmpty()) {
            adPool.poll()?.destroy()
        }
        Timber.d("All ads in the pool have been destroyed.")
    }
}