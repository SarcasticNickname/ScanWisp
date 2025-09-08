package com.myprojects.scanwisp.ui.state

import androidx.compose.runtime.Immutable

/**
 * Представляет состояние UI для длительных операций (загрузки, экспорта и т.д.).
 * @param isBusy Активна ли операция в данный момент.
 * @param message Текстовое сообщение для отображения (например, "Экспорт...").
 * @param progress Прогресс выполнения от 0.0 до 1.0. Если null, будет показан неопределенный индикатор.
 */
@Immutable
data class LoadingState(
    val isBusy: Boolean = false,
    val message: String? = null,
    val progress: Float? = null
)