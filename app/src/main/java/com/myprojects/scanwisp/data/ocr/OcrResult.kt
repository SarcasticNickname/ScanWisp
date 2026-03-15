package com.myprojects.scanwisp.data.ocr

import com.myprojects.scanwisp.domain.model.WordBox

/**
 * Составной результат одного прогона OCR.
 * [plainText]  — плоский текст, хранится в extractedText, показывается пользователю.
 * [wordBoxes]  — позиции слов, хранятся в wordBoxesJson, нужны только PDF-экспорту.
 */
data class OcrResult(
    val plainText: String,
    val wordBoxes: List<WordBox>
)