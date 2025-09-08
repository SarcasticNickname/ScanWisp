package com.myprojects.scanwisp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.myprojects.scanwisp.worker.CacheCleanupWorker
import com.myprojects.scanwisp.worker.GarbageCollectorWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ScanWispApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

//        MobileAds.initialize(this) {}
        setupRecurringWork()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun setupRecurringWork() {
        val workManager = WorkManager.getInstance(applicationContext)

        // Задача 1: Чистка кэша экспортов (раз в 7 дней)
        val cacheCleanupConstraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val cacheCleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(7, TimeUnit.DAYS)
            .setConstraints(cacheCleanupConstraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "cache-cleanup-work",
            ExistingPeriodicWorkPolicy.KEEP,
            cacheCleanupRequest
        )

        /**
         * ==========================================================
         * НОВОЕ ДОБАВЛЕНИЕ: Задача 2 - Физическое удаление "мягко удаленных" документов.
         * Запускается примерно раз в день.
         * ==========================================================
         */
        val garbageCollectorConstraints = Constraints.Builder()
            // Можно добавить требование к сети, если в будущем Worker будет что-то синхронизировать
            // .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val garbageCollectorRequest =
            PeriodicWorkRequestBuilder<GarbageCollectorWorker>(1, TimeUnit.DAYS)
                .setConstraints(garbageCollectorConstraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            "garbage-collector-work",
            ExistingPeriodicWorkPolicy.KEEP,
            garbageCollectorRequest
        )
    }
}