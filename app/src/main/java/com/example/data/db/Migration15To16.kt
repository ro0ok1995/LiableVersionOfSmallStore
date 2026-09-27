package com.example.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room Migration (v15 -> v16):
 * Schema alignment for database version 16.
 * No additional tables or columns required.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No-op: schema matches v15 entity definitions.
    }
}
