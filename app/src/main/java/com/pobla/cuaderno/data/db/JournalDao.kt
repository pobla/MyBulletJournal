package com.pobla.cuaderno.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    // Días
    @Query("SELECT * FROM days WHERE epochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<DayEntity?>

    @Query("SELECT * FROM days WHERE epochDay = :epochDay")
    suspend fun getDay(epochDay: Long): DayEntity?

    @Upsert
    suspend fun upsertDay(day: DayEntity)

    // Entradas
    @Query("SELECT * FROM entries WHERE epochDay = :epochDay ORDER BY position, id")
    fun observeEntries(epochDay: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntry(id: Long): EntryEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM entries WHERE epochDay = :epochDay")
    suspend fun nextEntryPosition(epochDay: Long): Int

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Delete
    suspend fun deleteEntry(entry: EntryEntity)

    // Hábitos
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position, id")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_checks WHERE epochDay = :epochDay")
    fun observeHabitChecks(epochDay: Long): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM habit_checks WHERE epochDay = :epochDay AND habitId = :habitId")
    suspend fun getHabitCheck(epochDay: Long, habitId: Long): HabitCheckEntity?

    @Upsert
    suspend fun upsertHabitCheck(check: HabitCheckEntity)
}
