package com.myprojects.scanwisp.domain.model

/**
 * Слово в режиме редактирования.
 *
 * Ключевой инвариант: [left]/[top]/[right]/[bottom] — координаты bounding box
 * в пикселях изображения — никогда не изменяются пользователем. Изменяется
 * только [text]. Это гарантирует корректность PDF text layer и оверлея
 * даже после правок.
 *
 * [confidence] — уверенность Tesseract (0.0–1.0), используется для окраски
 * чипов в редакторе: ≥0.75 зелёный, ≥0.40 жёлтый, иначе красный.
 */
data class EditableWord(
    val id: String,
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
    val isDeleted: Boolean = false
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    companion object {
        /** Создаёт редактируемый список из wordBoxesJson. */
        fun fromJson(json: String): List<EditableWord> =
            WordBox.fromJson(json).mapIndexed { i, box ->
                EditableWord(
                    id = i.toString(),
                    text = box.text,
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom,
                    confidence = box.confidence / 100f  // Tesseract даёт 0–100
                )
            }
    }

    fun toWordBox() = WordBox(
        text = text,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        confidence = confidence * 100f
    )
}

// --- Extension-функции на списке ---

/** Собирает plain text из не-удалённых слов. */
fun List<EditableWord>.toExtractedText(): String =
    filter { !it.isDeleted }.joinToString(" ") { it.text }

/** Сериализует не-удалённые слова обратно в wordBoxesJson. */
fun List<EditableWord>.toWordBoxJson(): String =
    WordBox.toJson(filter { !it.isDeleted }.map { it.toWordBox() })