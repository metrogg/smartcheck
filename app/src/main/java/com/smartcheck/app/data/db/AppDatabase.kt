package com.smartcheck.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

    @Database(
        entities = [UserEntity::class, RecordEntity::class],
        version = 5,
        exportSchema = false
    )
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun recordDao(): RecordDao

    companion object {
        val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) { database ->
            database.execSQL("ALTER TABLE users ADD COLUMN idCardNumber TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertImagePath TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertStartDate INTEGER")
            database.execSQL("ALTER TABLE users ADD COLUMN healthCertEndDate INTEGER")
        }

        val MIGRATION_2_3 = androidx.room.migration.Migration(2, 3) { database ->
            database.execSQL("ALTER TABLE check_records ADD COLUMN handStatus TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE check_records ADD COLUMN healthCertStatus TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE check_records ADD COLUMN symptomFlags TEXT NOT NULL DEFAULT ''")
        }

        val MIGRATION_3_4 = androidx.room.migration.Migration(3, 4) { database ->
            database.execSQL("ALTER TABLE check_records ADD COLUMN faceImagePath TEXT")
            database.execSQL("ALTER TABLE check_records ADD COLUMN handPalmPath TEXT")
            database.execSQL("ALTER TABLE check_records ADD COLUMN handBackPath TEXT")
        }

        val MIGRATION_4_5 = androidx.room.migration.Migration(4, 5) { database ->
            database.execSQL("ALTER TABLE users ADD COLUMN faceImagePath TEXT")
        }
    }
}
