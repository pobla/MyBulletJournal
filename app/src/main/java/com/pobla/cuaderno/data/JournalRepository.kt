package com.pobla.cuaderno.data

import androidx.room.withTransaction
import com.pobla.cuaderno.data.db.AppDatabase
import com.pobla.cuaderno.data.db.DayEntity
import com.pobla.cuaderno.data.db.EntryEntity
import com.pobla.cuaderno.data.db.HabitCheckEntity
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Todo lo que se guarda de un día, tal cual sale de la base de datos. */
data class DayData(
    val day: DayEntity?,
    val entries: List<EntryEntity>,
    val habits: List<HabitEntity>,
    val checks: List<HabitCheckEntity>,
)

/**
 * Punto único de acceso a los datos. Cualquier cambio en un día lo devuelve a
 * "pendiente de pasar al cuaderno".
 */
class JournalRepository(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.journalDao()

    fun observeDay(date: LocalDate): Flow<DayData> {
        val d = date.toEpochDay()
        return combine(
            dao.observeDay(d),
            dao.observeEntries(d),
            dao.observeActiveHabits(),
            dao.observeHabitChecks(d),
        ) { day, entries, habits, checks -> DayData(day, entries, habits, checks) }
    }

    suspend fun addEntry(date: LocalDate, type: EntryType, text: String) = db.withTransaction {
        val d = date.toEpochDay()
        val t = now()
        dao.insertEntry(
            EntryEntity(
                epochDay = d,
                type = type,
                text = text,
                position = dao.nextEntryPosition(d),
                createdAt = t,
                updatedAt = t,
            ),
        )
        markChanged(d, t)
    }

    suspend fun cycleTaskStatus(entryId: Long) = db.withTransaction {
        val entry = dao.getEntry(entryId) ?: return@withTransaction
        if (entry.type != EntryType.TASK) return@withTransaction
        val t = now()
        dao.updateEntry(entry.copy(status = entry.status.next(), updatedAt = t))
        markChanged(entry.epochDay, t)
    }

    suspend fun updateEntry(entryId: Long, type: EntryType, status: TaskStatus, text: String) =
        db.withTransaction {
            val entry = dao.getEntry(entryId) ?: return@withTransaction
            val newStatus = if (type == EntryType.TASK) status else TaskStatus.OPEN
            if (entry.type == type && entry.status == newStatus && entry.text == text) {
                return@withTransaction
            }
            val t = now()
            dao.updateEntry(entry.copy(type = type, status = newStatus, text = text, updatedAt = t))
            markChanged(entry.epochDay, t)
        }

    suspend fun deleteEntry(entryId: Long) = db.withTransaction {
        val entry = dao.getEntry(entryId) ?: return@withTransaction
        dao.deleteEntry(entry)
        markChanged(entry.epochDay, now())
    }

    suspend fun toggleHabit(date: LocalDate, habitId: Long) = db.withTransaction {
        val d = date.toEpochDay()
        val t = now()
        val current = dao.getHabitCheck(d, habitId)?.done ?: false
        dao.upsertHabitCheck(HabitCheckEntity(d, habitId, done = !current, updatedAt = t))
        markChanged(d, t)
    }

    suspend fun setSummary(date: LocalDate, summary: String) = db.withTransaction {
        val d = date.toEpochDay()
        val day = dao.getDay(d) ?: DayEntity(epochDay = d)
        if (day.summary == summary) return@withTransaction
        val t = now()
        dao.upsertDay(
            day.copy(summary = summary, summaryUpdatedAt = t, transcribed = false, updatedAt = t),
        )
    }

    suspend fun setTranscribed(date: LocalDate, transcribed: Boolean) = db.withTransaction {
        val d = date.toEpochDay()
        val day = dao.getDay(d) ?: DayEntity(epochDay = d)
        dao.upsertDay(
            if (transcribed) {
                day.copy(transcribed = true, lastTranscribedAt = now())
            } else {
                day.copy(transcribed = false)
            },
        )
    }

    private suspend fun markChanged(epochDay: Long, t: Long) {
        val day = dao.getDay(epochDay) ?: DayEntity(epochDay = epochDay)
        dao.upsertDay(day.copy(transcribed = false, updatedAt = t))
    }
}
