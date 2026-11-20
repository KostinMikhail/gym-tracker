package com.gym.tracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
