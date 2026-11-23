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
    ],
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
