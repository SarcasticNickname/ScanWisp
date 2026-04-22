package com.myprojects.scanwisp.domain.model

/**
 * Согласует позиции слов после свободного редактирования текста.
 *
 * Гарантия: результат никогда не пустой при непустом вводе,
 * wordBoxesJson никогда не становится null.
 *
 * Два режима:
 * - Лёгкая правка (≤2× изменения) → LCS diff, позиции точные.
 * - Кардинальная правка (>2× изменения) → line-level fallback, позиции приближённые.
 */
object WordBoxReconciler {

    fun reconcile(
        originalWords: List<EditableWord>,
        newText: String
    ): List<EditableWord> {
        val newWords = newText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (newWords.isEmpty()) return emptyList()
        if (originalWords.isEmpty()) return newWords.mapIndexed { i, w -> fallbackWord(w, i) }

        val ratio = newWords.size.toFloat() / originalWords.size
        return if (ratio > 2f || ratio < 0.5f) {
            assignToLines(newWords, originalWords)
        } else {
            alignWithDiff(originalWords, newWords)
        }
    }

    // ─── LCS diff ────────────────────────────────────────────────────────────

    private enum class Op { KEEP, REPLACE, DELETE, INSERT }

    private fun alignWithDiff(
        old: List<EditableWord>,
        new: List<String>
    ): List<EditableWord> {
        val ops = lcsDiff(
            old.map { it.text.lowercase() },
            new.map { it.lowercase() }
        )
        val result = mutableListOf<EditableWord>()
        var oi = 0;
        var ni = 0

        for (op in ops) {
            when (op) {
                Op.KEEP -> {
                    result += old[oi].copy(text = new[ni])
                    oi++; ni++
                }

                Op.REPLACE -> {
                    result += old[oi].copy(text = new[ni], confidence = 0f)
                    oi++; ni++
                }

                Op.DELETE -> {
                    oi++
                }

                Op.INSERT -> {
                    val prev = result.lastOrNull()
                    val next = old.getOrNull(oi)
                    val (l, t, r, b) = interpolate(prev, next)
                    result += EditableWord(
                        id = "ins_${result.size}", text = new[ni],
                        left = l, top = t, right = r, bottom = b,
                        confidence = 0f
                    )
                    ni++
                }
            }
        }
        return result
    }

    private fun lcsDiff(oldSeq: List<String>, newSeq: List<String>): List<Op> {
        val m = oldSeq.size;
        val n = newSeq.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (oldSeq[i - 1] == newSeq[j - 1]) dp[i - 1][j - 1] + 1
            else maxOf(dp[i - 1][j], dp[i][j - 1])
        }

        val ops = mutableListOf<Op>()
        var i = m;
        var j = n
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && oldSeq[i - 1] == newSeq[j - 1] -> {
                    ops += Op.KEEP; i--; j--
                }

                i > 0 && j > 0 && dp[i - 1][j] == dp[i][j - 1] -> {
                    ops += Op.REPLACE; i--; j--
                }

                j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                    ops += Op.INSERT; j--
                }

                else -> {
                    ops += Op.DELETE; i--
                }
            }
        }
        return ops.reversed()
    }

    // ─── Line-level fallback ──────────────────────────────────────────────────

    private fun assignToLines(
        newWords: List<String>,
        original: List<EditableWord>
    ): List<EditableWord> {
        val avgH = original.map { it.height }.average().toInt().coerceAtLeast(10)
        val threshold = avgH / 2

        // Кластеризуем исходные слова в строки по Y
        val sorted = original.sortedBy { it.top }
        val lines = mutableListOf<MutableList<EditableWord>>()
        var cur = mutableListOf(sorted.first())
        for (w in sorted.drop(1)) {
            if (w.top - cur.last().top > threshold) {
                lines += cur; cur = mutableListOf()
            }
            cur += w
        }
        lines += cur

        // Раскидываем новые слова по строкам пропорционально
        val result = mutableListOf<EditableWord>()
        val perLine = (newWords.size.toFloat() / lines.size).coerceAtLeast(1f)
        var wi = 0

        lines.forEachIndexed { li, lineWords ->
            val count = if (li == lines.lastIndex) newWords.size - wi
            else minOf((perLine + 0.5f).toInt(), newWords.size - wi)
            val slice = newWords.subList(wi, (wi + count).coerceAtMost(newWords.size))
            wi += slice.size

            val lineLeft = lineWords.minOf { it.left }
            val lineRight = lineWords.maxOf { it.right }
            val avgW =
                if (lineWords.isNotEmpty()) lineWords.map { it.width }.average().toInt() else 60
            val lineTop = lineWords.first().top
            val lineBot = lineWords.maxOf { it.bottom }

            slice.forEachIndexed { si, word ->
                val frac = if (slice.size > 1) si.toFloat() / (slice.size - 1) else 0f
                val approxLeft = (lineLeft + frac * (lineRight - lineLeft)).toInt()
                result += EditableWord(
                    id = "ln${li}_$si", text = word,
                    left = approxLeft, top = lineTop,
                    right = approxLeft + avgW, bottom = lineBot,
                    confidence = 0f
                )
            }
        }

        // Остаток (если новых слов больше строк)
        while (wi < newWords.size) {
            result += fallbackWord(newWords[wi], result.size, after = original.last())
            wi++
        }

        return result
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun interpolate(prev: EditableWord?, next: EditableWord?): Rect {
        return when {
            prev != null && next != null -> {
                val gL = prev.right;
                val gR = next.left
                val avgW = (prev.width + next.width) / 2
                val avgT = (prev.top + next.top) / 2
                val avgB = (prev.bottom + next.bottom) / 2
                if (gR > gL + 4) {
                    val mid = (gL + gR) / 2
                    Rect(mid - avgW / 2, avgT, mid + avgW / 2, avgB)
                } else {
                    Rect(prev.right, prev.top, prev.right + prev.width, prev.bottom)
                }
            }

            prev != null -> Rect(prev.right + 4, prev.top, prev.right + 4 + prev.width, prev.bottom)
            next != null -> {
                val l = maxOf(0, next.left - 4 - next.width)
                Rect(l, next.top, l + next.width, next.bottom)
            }

            else -> Rect(10, 10, 100, 30)
        }
    }

    private fun fallbackWord(word: String, index: Int, after: EditableWord? = null): EditableWord {
        val l = after?.let { it.right + 4 + index * 60 } ?: (10 + index * 60)
        val t = after?.top ?: 10
        return EditableWord(
            id = "fb_$index",
            text = word,
            left = l,
            top = t,
            right = l + 60,
            bottom = t + 20,
            confidence = 0f
        )
    }
}