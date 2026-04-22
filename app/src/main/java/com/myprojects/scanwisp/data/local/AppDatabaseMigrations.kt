package com.myprojects.scanwisp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {

    /**
     * Миграция 1 → 2:
     * - Удаляет FTS4-таблицу documents_fts (Room-managed, только по названию)
     * - Создаёт FTS5-таблицу pages_fts для полнотекстового поиска по OCR-тексту
     * - Заполняет pages_fts из страниц, у которых уже есть extractedText
     *
     * Поиск по названию документов переведён на LIKE-запрос — отдельной FTS-таблицы
     * для заголовков больше не нужно.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // DROP FTS4 virtual table — SQLite автоматически удаляет все shadow-таблицы
            database.execSQL("DROP TABLE IF EXISTS documents_fts")

            // Создаём FTS5-таблицу для поиска по тексту страниц.
            // page_id, document_owner_id, page_number — UNINDEXED: хранятся, но не индексируются.
            // extracted_text — единственная индексируемая колонка.
            database.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS pages_fts USING fts5(
                    page_id            UNINDEXED,
                    document_owner_id  UNINDEXED,
                    page_number        UNINDEXED,
                    extracted_text,
                    tokenize='unicode61 remove_diacritics 1'
                )
                """.trimIndent()
            )

            // Заполняем начальные данные из уже распознанных страниц
            database.execSQL(
                """
                INSERT INTO pages_fts(page_id, document_owner_id, page_number, extracted_text)
                SELECT id, documentOwnerId, pageNumber, extractedText
                FROM pages
                WHERE extractedText IS NOT NULL AND extractedText != ''
                """.trimIndent()
            )
        }
    }
}