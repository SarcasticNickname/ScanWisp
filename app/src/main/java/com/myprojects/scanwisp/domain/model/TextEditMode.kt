package com.myprojects.scanwisp.domain.model

sealed class TextEditMode {
    /** Редактор токенов: чипы слов с цветом confidence. Позиции сохраняются. */
    object Token : TextEditMode()

    /** Свободный редактор: обычный TextField. Позиции восстанавливаются через reconcile. */
    object FreeText : TextEditMode()
}