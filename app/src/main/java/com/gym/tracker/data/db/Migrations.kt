package com.gym.tracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * История схемы:
 *  v1 — исходная;
 *  v2 (релизы 1.0.10–1.0.11) — очередь выгрузки в Google Таблицу `sync_ops`;
 *  v3 — выгрузка убрана, `sync_ops` удаляется. Схема v3 = схема v1.
 * Миграции 1→2 и 2→3 нужны обе: на телефоне может быть любая из прежних версий.
 */

/**
 * 1 → 2: очередь выгрузки в Google Таблицу (`sync_ops`). Существующие таблицы и данные не меняются.
 * SQL совпадает с app/schemas/…/2.json (проверяет MigrationTest).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sync_ops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entryId` TEXT NOT NULL, `op` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`attempts` INTEGER NOT NULL, `lastError` TEXT)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_ops_entryId` ON `sync_ops` (`entryId`)")
    }
}

/** 2 → 3: выгрузка в таблицу убрана — удаляется только очередь `sync_ops`, данные тренировок не трогаются. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_sync_ops_entryId`")
        db.execSQL("DROP TABLE IF EXISTS `sync_ops`")
    }
}
