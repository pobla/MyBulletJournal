package com.pobla.cuaderno.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DayEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        EntryEntity::class,
        PhotoEntity::class,
        MonthEntryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        /** v1 → v2: borrado blando de entradas y marca de tiempo en hábitos (para la nube). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3: entradas creadas en la web (id de Firestore). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN remoteId TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_remoteId` ON `entries` (`remoteId`)")
            }
        }

        /** v3 → v4: a qué día se migró una tarea. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN migratedTo INTEGER")
            }
        }

        /** v4 → v5: entradas por mes (tareas del mes y registro futuro). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `month_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ym` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `status` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL DEFAULT 0, `remoteId` TEXT, `migratedTo` INTEGER)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_month_entries_ym` ON `month_entries` (`ym`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_month_entries_remoteId` ON `month_entries` (`remoteId`)",
                )
            }
        }

        /** v5 → v6: tiempo y ánimo del día. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN weather TEXT")
                db.execSQL("ALTER TABLE days ADD COLUMN mood INTEGER")
                db.execSQL("ALTER TABLE days ADD COLUMN trackersUpdatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase {
            // Hábitos iniciales en el idioma del móvil al instalar.
            val defaultHabits = context.resources.getStringArray(com.pobla.cuaderno.R.array.default_habits)
            return Room.databaseBuilder(context, AppDatabase::class.java, "cuaderno.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        defaultHabits.forEachIndexed { index, name ->
                            db.execSQL(
                                "INSERT INTO habits (name, position, archived) VALUES (?, ?, 0)",
                                arrayOf<Any?>(name, index),
                            )
                        }
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }
    }
}
