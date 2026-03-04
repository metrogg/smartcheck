package com.smartcheck.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

    @Database(
        entities = [UserEntity::class, RecordEntity::class, ApiTokenEntity::class, ApiAccessLogEntity::class, SystemUserEntity::class],
        version = 7,
        exportSchema = false
    )
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun recordDao(): RecordDao
    abstract fun apiTokenDao(): ApiTokenDao
    abstract fun apiAccessLogDao(): ApiAccessLogDao
    abstract fun systemUserDao(): SystemUserDao

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

        val MIGRATION_5_6 = androidx.room.migration.Migration(5, 6) { database ->
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS api_tokens (
                    token TEXT PRIMARY KEY NOT NULL,
                    userId INTEGER NOT NULL,
                    username TEXT NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    expiresAt INTEGER NOT NULL,
                    isRevoked INTEGER NOT NULL DEFAULT 0,
                    lastUsedAt INTEGER
                )
            """)
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS api_access_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    endpoint TEXT NOT NULL,
                    method TEXT NOT NULL,
                    requestParams TEXT,
                    responseCode INTEGER NOT NULL,
                    responseMessage TEXT,
                    userId INTEGER,
                    username TEXT,
                    ipAddress TEXT,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """)
        }

        val MIGRATION_6_7 = androidx.room.migration.Migration(6, 7) { database ->
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS system_users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    username TEXT NOT NULL UNIQUE,
                    passwordHash TEXT NOT NULL,
                    passwordType TEXT NOT NULL DEFAULT 'plain',
                    employeeId TEXT,
                    role TEXT NOT NULL DEFAULT 'employee',
                    status TEXT NOT NULL DEFAULT 'active',
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0
                )
            """)
        }
    }
}
