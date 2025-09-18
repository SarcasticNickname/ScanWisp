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
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ScanWispApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setupRecurringWork()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun setupRecurringWork() {
        val workManager = WorkManager.getInstance(applicationContext)

        // Задача 1: Чистка кэша экспортов
        val cacheCleanupConstraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()
        val cacheCleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(7, TimeUnit.DAYS)
            .setConstraints(cacheCleanupConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            "cache-cleanup-work",
            ExistingPeriodicWorkPolicy.UPDATE, // Используем UPDATE
            cacheCleanupRequest
        )

        // Задача 2: Физическое удаление "мягко удаленных" документов
        val garbageCollectorConstraints = Constraints.Builder().build()
        val garbageCollectorRequest =
            PeriodicWorkRequestBuilder<GarbageCollectorWorker>(1, TimeUnit.DAYS)
                .setConstraints(garbageCollectorConstraints)
                .build()
        workManager.enqueueUniquePeriodicWork(
            "garbage-collector-work",
            ExistingPeriodicWorkPolicy.UPDATE, // Используем UPDATE
            garbageCollectorRequest
        )
    }
}