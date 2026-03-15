package com.myprojects.scanwisp.domain.model

/**
 * Слово с координатами из Tesseract (в пикселях изображения).
 * left/top/right/bottom — координаты bounding box слова.
 * Используется только для построения текстового слоя в searchable PDF.
 * Пользователь этот объект не видит и не редактирует.
 */
data class WordBox(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float
) {
    companion object {
        fun toJson(words: List<WordBox>): String {
            val arr = org.json.JSONArray()
            words.forEach { w ->
                arr.put(org.json.JSONObject().apply {
                    put("t", w.text)
                    put("l", w.left)
                    put("to", w.top)
                    put("r", w.right)
                    put("b", w.bottom)
                    put("c", w.confidence.toDouble())
                })
            }
            return arr.toString()
        }

        fun fromJson(json: String): List<WordBox> {
            val arr = org.json.JSONArray(json)
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                WordBox(
                    text       = o.getString("t"),
                    left       = o.getInt("l"),
                    top        = o.getInt("to"),
                    right      = o.getInt("r"),
                    bottom     = o.getInt("b"),
                    confidence = o.getDouble("c").toFloat()
                )
            }
        }
    }
}