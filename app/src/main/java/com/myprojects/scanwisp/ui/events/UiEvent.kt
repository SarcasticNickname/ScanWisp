package com.myprojects.scanwisp.ui.events

import android.content.IntentSender
import androidx.compose.runtime.Stable
import com.myprojects.scanwisp.domain.model.AppError
import java.io.File

/**
 * Запечатанный класс для представления одноразовых UI-событий,
 * отправляемых из ViewModel в UI-слой.
 */
@Stable
sealed class UiEvent {
    /**
     * Событие для показа Snackbar.
     * @param message Текст сообщения.
     * @param actionLabel Текст на кнопке действия (например, "Отменить").
     * @param isError Указывает, является ли это сообщением об ошибке (для стилизации).
     */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val isError: Boolean = false
    ) : UiEvent()

    /**
     * Событие для запуска системного ShareSheet (ACTION_SEND).
     * @param intent Готовый интент для запуска.
     * @param tempFile Временный файл, созданный для этого интента, который нужно удалить после использования.
     */
    data class LaunchShareIntent(val intent: android.content.Intent, val tempFile: File) : UiEvent()

    /**
     * Событие для запуска системного файлового менеджера для сохранения (ACTION_CREATE_DOCUMENT).
     * @param intent Готовый интент для запуска.
     * @param tempFile Временный файл, созданный для этого интента, который нужно удалить после использования.
     */
    data class LaunchSaveIntent(val intent: android.content.Intent, val tempFile: File) : UiEvent()

    /**
     *  Событие для запуска сканера ML Kit.
     * @param intentSender Объект для запуска UI сканера.
     */
    data class LaunchScanner(val intentSender: IntentSender) : UiEvent()

    /**
     * Событие для показа диалогового окна с типизированной ошибкой.
     */
    data class ShowErrorDialog(val error: AppError) : UiEvent()

    /**
     * Событие, которое указывает UI выполнить навигацию назад.
     */
    object NavigateBack : UiEvent()

    /**
     * Событие для запроса показа диалога оценки приложения.
     */
    object RequestInAppReview : UiEvent()
}