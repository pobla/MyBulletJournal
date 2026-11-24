package com.pobla.cuaderno.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Días con algo apuntado (o cambios) que no están marcados como pasados al cuaderno. */
const val PENDING_DAYS_QUERY = """
    SELECT * FROM (
        SELECT d.epochDay AS epochDay,
               d.summary AS summary,
               d.lastTranscribedAt AS lastTranscribedAt,
               (d.weather IS NOT NULL OR d.mood IS NOT NULL) AS hasTrackers,
               (SELECT COUNT(*) FROM entries e WHERE e.epochDay = d.epochDay AND e.deleted = 0) AS entryCount,
               (SELECT COUNT(*) FROM habit_checks h WHERE h.epochDay = d.epochDay AND h.done = 1) AS habitCount
        FROM days d
        WHERE d.transcribed = 0
    )
    WHERE entryCount > 0 OR habitCount > 0 OR TRIM(summary) != '' OR lastTranscribedAt IS NOT NULL OR hasTrackers
    ORDER BY epochDay
"""

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
    @Query("SELECT * FROM entries WHERE epochDay = :epochDay AND deleted = 0 ORDER BY position, id")
    fun observeEntries(epochDay: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE epochDay = :epochDay AND deleted = 0 ORDER BY position, id")
    suspend fun getEntries(epochDay: Long): List<EntryEntity>

    /** Emite cada vez que cambia la tabla de entradas (para refrescar el widget). */
    @Query("SELECT COUNT(*) FROM entries")
    fun observeEntriesChanges(): Flow<Int>

    @Query("SELECT * FROM entries WHERE remoteId = :remoteId")
    suspend fun getEntryByRemoteId(remoteId: String): EntryEntity?

    @Upsert
    suspend fun upsertEntry(entry: EntryEntity): Long

    @Upsert
    suspend fun upsertHabit(habit: HabitEntity)

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

    // Rangos (vista semanal)
    @Query("SELECT * FROM days WHERE epochDay BETWEEN :from AND :to")
    fun observeDaysInRange(from: Long, to: Long): Flow<List<DayEntity>>

    @Query("SELECT * FROM entries WHERE epochDay BETWEEN :from AND :to AND deleted = 0 ORDER BY epochDay, position, id")
    fun observeEntriesInRange(from: Long, to: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM habit_checks WHERE epochDay BETWEEN :from AND :to")
    fun observeHabitChecksInRange(from: Long, to: Long): Flow<List<HabitCheckEntity>>

    // Pendientes de pasar al cuaderno
    @Query(PENDING_DAYS_QUERY)
    fun observePendingDays(): Flow<List<PendingDayRow>>

    @Query(PENDING_DAYS_QUERY)
    suspend fun getPendingDays(): List<PendingDayRow>

    @Query("SELECT * FROM habit_checks WHERE epochDay = :epochDay")
    suspend fun getHabitChecks(epochDay: Long): List<HabitCheckEntity>

    // Hábitos
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position, id")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    /** Activos + archivados que se marcaron ese día (para no perder el historial). */
    @Query(
        """
        SELECT * FROM habits
        WHERE archived = 0
           OR id IN (SELECT habitId FROM habit_checks WHERE epochDay = :epochDay AND done = 1)
        ORDER BY position, id
        """,
    )
    fun observeHabitsForDay(epochDay: Long): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position, id")
    suspend fun getActiveHabits(): List<HabitEntity>

    // Sincronización con la nube: lo cambiado desde la última copia
    @Query("SELECT * FROM days WHERE updatedAt > :since")
    suspend fun daysChangedSince(since: Long): List<DayEntity>

    @Query("SELECT * FROM entries WHERE updatedAt > :since")
    suspend fun entriesChangedSince(since: Long): List<EntryEntity>

    @Query("SELECT * FROM habits WHERE updatedAt > :since")
    suspend fun habitsChangedSince(since: Long): List<HabitEntity>

    @Query("SELECT * FROM habit_checks WHERE updatedAt > :since")
    suspend fun habitChecksChangedSince(since: Long): List<HabitCheckEntity>

    /** Emite con cualquier cambio en cualquier tabla (para programar la copia). */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM days) + (SELECT COUNT(*) FROM entries)
             + (SELECT COUNT(*) FROM habits) + (SELECT COUNT(*) FROM habit_checks)
             + (SELECT COUNT(*) FROM month_entries)
        """,
    )
    fun observeAnyChange(): Flow<Int>

    // Entradas por mes (tareas del mes y registro futuro)
    @Query("SELECT * FROM month_entries WHERE ym BETWEEN :from AND :to AND deleted = 0 ORDER BY ym, position, id")
    fun observeMonthEntries(from: Int, to: Int): Flow<List<MonthEntryEntity>>

    @Query("SELECT * FROM month_entries WHERE id = :id")
    suspend fun getMonthEntry(id: Long): MonthEntryEntity?

    @Query("SELECT * FROM month_entries WHERE remoteId = :remoteId")
    suspend fun getMonthEntryByRemoteId(remoteId: String): MonthEntryEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM month_entries WHERE ym = :ym")
    suspend fun nextMonthPosition(ym: Int): Int

    @Insert
    suspend fun insertMonthEntry(entry: MonthEntryEntity): Long

    @Update
    suspend fun updateMonthEntry(entry: MonthEntryEntity)

    @Upsert
    suspend fun upsertMonthEntry(entry: MonthEntryEntity): Long

    @Query("SELECT * FROM month_entries WHERE updatedAt > :since")
    suspend fun monthEntriesChangedSince(since: Long): List<MonthEntryEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabit(id: Long): HabitEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habits")
    suspend fun nextHabitPosition(): Int

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabits(habits: List<HabitEntity>)

    @Query("SELECT * FROM habit_checks WHERE epochDay = :epochDay")
    fun observeHabitChecks(epochDay: Long): Flow<List<HabitCheckEntity>>

    @Query("SELECT * FROM habit_checks WHERE epochDay = :epochDay AND habitId = :habitId")
    suspend fun getHabitCheck(epochDay: Long, habitId: Long): HabitCheckEntity?

    @Upsert
    suspend fun upsertHabitCheck(check: HabitCheckEntity)
}
