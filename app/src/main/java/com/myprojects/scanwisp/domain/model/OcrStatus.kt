package com.myprojects.scanwisp.domain.model

enum class OcrStatus {
    PENDING,      // страница добавлена, OCR ещё не запускался
    IN_PROGRESS,  // воркер взял страницу в работу
    DONE,         // распознавание завершено
    FAILED        // ошибка, можно повторить вручную
}