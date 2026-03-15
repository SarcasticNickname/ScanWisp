package com.myprojects.scanwisp.data.ocr

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrWordFilter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dictionary: Set<String> by lazy { loadDictionary() }

    fun isPlausible(word: String, confidence: Float): Boolean {
        if (word.isBlank()) return false
        if (word.all { it.isDigit() || it in ".,:-/%" }) return true
        if (confidence >= HIGH_CONFIDENCE) return true
        if (confidence >= LOW_CONFIDENCE) {
            val normalized = word.lowercase()
                .trim('.', ',', ':', ';', '!', '?', '«', '»', '"', '(', ')', '—', '-')
            if (normalized.length < 2) return confidence >= HIGH_CONFIDENCE
            return normalized in dictionary
        }
        return false
    }

    private fun loadDictionary(): Set<String> {
        val words = HashSet<String>(500_000)
        listOf("dict/russian.txt", "dict/english.txt").forEach { path ->
            try {
                context.assets.open(path).use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        reader.forEachLine { line ->
                            val w = line.trim().lowercase()
                            if (w.isNotEmpty()) words.add(w)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Dictionary not found: $path")
            }
        }
        Timber.d("Dictionary loaded: ${words.size} words")
        return words
    }

    companion object {
        private const val HIGH_CONFIDENCE = 70f
        private const val LOW_CONFIDENCE = 30f
    }
}