package com.myprojects.scanwisp.domain.model

enum class OcrLanguage(val tessLang: String) {
    RUSSIAN("rus"),
    ENGLISH("eng"),
    RUSSIAN_ENGLISH("rus+eng")
}