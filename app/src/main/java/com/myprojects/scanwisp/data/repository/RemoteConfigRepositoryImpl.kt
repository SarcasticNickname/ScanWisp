// Файл: app/src/main/java/com/myprojects/scanwisp/data/repository/RemoteConfigRepositoryImpl.kt

package com.myprojects.scanwisp.data.repository

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.myprojects.scanwisp.domain.repository.RemoteConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

// Ключи, которые мы используем в Firebase Remote Config Console и в remote_config_defaults.xml
private const val KEY_NATIVE_AD_ENABLED = "native_ad_enabled"
private const val KEY_NATIVE_AD_START_POSITION = "native_ad_start_position"
private const val KEY_NATIVE_AD_INTERVAL = "native_ad_interval"

// Значения по умолчанию на случай сбоя
private const val DEFAULT_NATIVE_AD_START_POSITION = 4L
private const val DEFAULT_NATIVE_AD_INTERVAL = 8L

@Singleton
class RemoteConfigRepositoryImpl @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) : RemoteConfigRepository {

    override fun isNativeAdEnabled(): Boolean {
        return remoteConfig.getBoolean(KEY_NATIVE_AD_ENABLED)
    }

    override fun getNativeAdStartPosition(): Int {
        return remoteConfig.getLong(KEY_NATIVE_AD_START_POSITION).toInt()
    }

    override fun getNativeAdInterval(): Int {
        val interval = remoteConfig.getLong(KEY_NATIVE_AD_INTERVAL).toInt()
        // Защита от невалидного значения (интервал не может быть меньше 1).
        // Если из конфига придет 0 или отрицательное число, используем дефолтное значение.
        return if (interval > 0) interval else DEFAULT_NATIVE_AD_INTERVAL.toInt()
    }

    override fun fetchAndActivate() {
        // Эта функция запрашивает у сервера Firebase последние значения
        // и, в случае успеха, делает их доступными для приложения.
        remoteConfig.fetchAndActivate()
    }
}