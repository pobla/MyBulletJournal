package com.pobla.cuaderno.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DayEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        EntryEntity::class,
        PhotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        val DEFAULT_HABITS = listOf("Creatina", "Vitamina D", "Duolingo", "Ejercicio")

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "cuaderno.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_HABITS.forEachIndexed { index, name ->
                            db.execSQL(
                                "INSERT INTO habits (name, position, archived) VALUES (?, ?, 0)",
                                arrayOf<Any?>(name, index),
                            )
                        }
                    }
                })
                .build()
    }
}
