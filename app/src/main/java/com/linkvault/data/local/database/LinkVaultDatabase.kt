package com.linkvault.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.linkvault.data.local.dao.FolderDao
import com.linkvault.data.local.dao.LinkDao
import com.linkvault.data.local.dao.DownloadDao
import com.linkvault.data.local.entity.FolderEntity
import com.linkvault.data.local.entity.LinkEntity
import com.linkvault.data.local.entity.DownloadEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, LinkEntity::class, DownloadEntity::class],
    version = 3,
    exportSchema = true
)
abstract class LinkVaultDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun linkDao(): LinkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE links ADD COLUMN artist TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE links ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE links ADD COLUMN releaseYear TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
