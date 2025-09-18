package com.myprojects.scanwisp.domain.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface AppError {
    /** Ошибка при загрузке данных из базы (для полноэкранных состояний). */
    data object LoadDataError : AppError

    /** Ошибка при выполнении операции с базой (сохранение, удаление, обновление). */
    data object DatabaseOperationError : AppError

    /** Ошибка при экспорте файла (PDF, ZIP, JPEG). */
    data object ExportError : AppError

    /** Ошибка при обработке изображения (сканирование, замена). */
    data object ImageProcessingError : AppError

    /** Ошибка, связанная с запуском внешнего модуля (например, ML Kit Scanner). */
    data object ScannerLaunchError : AppError

    data object FileSystemError : AppError

    data object NotEnoughStorageError : AppError

    /** Общая, непредвиденная ошибка. */
    data class General(val message: String? = null) : AppError
}