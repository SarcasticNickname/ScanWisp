package com.myprojects.scanwisp.consent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Синглтон, который хранит и предоставляет состояние согласия пользователя (UMP).
 * Выступает в роли "светофора", разрешающего или запрещающего запросы рекламы
 * в масштабах всего приложения.
 */
@Singleton
class ConsentManager @Inject constructor() {

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds = _canRequestAds.asStateFlow()

    /**
     * Обновляет статус согласия. Вызывается из MainActivity после завершения
     * процесса UMP.
     * @param canRequest `true`, если запросы рекламы разрешены, иначе `false`.
     */
    fun updateConsentStatus(canRequest: Boolean) {
        _canRequestAds.value = canRequest
    }
}