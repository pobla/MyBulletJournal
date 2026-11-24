package com.pobla.cuaderno.data

import androidx.room.withTransaction
import com.pobla.cuaderno.data.db.AppDatabase
import com.pobla.cuaderno.data.db.DayEntity
import com.pobla.cuaderno.data.db.EntryEntity
import com.pobla.cuaderno.data.db.HabitCheckEntity
import com.pobla.cuaderno.data.db.HabitEntity
import com.pobla.cuaderno.data.db.PendingDayRow
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.ImportItem
import com.pobla.cuaderno.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import com.pobla.cuaderno.data.db.MonthEntryEntity
import java.time.YearMonth
import com.pobla.cuaderno.model.Mood
import com.pobla.cuaderno.model.Weather

/** Todo lo que se guarda de un día, tal cual sale de la base de datos. */
data class DayData(
    val day: DayEntity?,
    val entries: List<EntryEntity>,
    val habits: List<HabitEntity>,
    val checks: List<HabitCheckEntity>,
)

/** Datos de un rango de días (vista semanal). */
data class RangeData(
    val days: List<DayEntity>,
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
            dao.observeHabitsForDay(d),
            dao.observeHabitChecks(d),
        ) { day, entries, habits, checks -> DayData(day, entries, habits, checks) }
    }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<RangeData> {
        val f = from.toEpochDay()
        val t = to.toEpochDay()
        return combine(
            dao.observeDaysInRange(f, t),
            dao.observeEntriesInRange(f, t),
            dao.observeActiveHabits(),
            dao.observeHabitChecksInRange(f, t),
        ) { days, entries, habits, checks -> RangeData(days, entries, habits, checks) }
    }

    fun observePendingDays(): Flow<List<PendingDayRow>> = dao.observePendingDays()

    fun observeActiveHabits(): Flow<List<HabitEntity>> = dao.observeActiveHabits()

    // Para el widget
    suspend fun getEntries(date: LocalDate): List<EntryEntity> = dao.getEntries(date.toEpochDay())

    fun observeEntriesChanges(): Flow<Int> = dao.observeEntriesChanges()

    // Para los recordatorios
    suspend fun getPendingDays(): List<PendingDayRow> = dao.getPendingDays()

    /** Nombres de los hábitos activos que no se han marcado en [date]. */
    suspend fun getMissingHabits(date: LocalDate): List<String> {
        val done = dao.getHabitChecks(date.toEpochDay()).filter { it.done }.map { it.habitId }.toSet()
        return dao.getActiveHabits().filter { it.id !in done }.map { it.name }
    }

    // Gestión de hábitos (no afecta a "pasado al cuaderno")
    suspend fun addHabit(name: String) = db.withTransaction {
        dao.insertHabit(HabitEntity(name = name, position = dao.nextHabitPosition(), updatedAt = now()))
    }

    suspend fun renameHabit(id: Long, name: String) = db.withTransaction {
        val habit = dao.getHabit(id) ?: return@withTransaction
        dao.updateHabits(listOf(habit.copy(name = name, updatedAt = now())))
    }

    /** Se archiva en lugar de borrar para conservar el historial de días pasados. */
    suspend fun archiveHabit(id: Long) = db.withTransaction {
        val habit = dao.getHabit(id) ?: return@withTransaction
        dao.updateHabits(listOf(habit.copy(archived = true, updatedAt = now())))
    }

    suspend fun moveHabit(id: Long, offset: Int) = db.withTransaction {
        val list = dao.getActiveHabits().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in list.indices) return@withTransaction
        list.add(to, list.removeAt(from))
        val t = now()
        dao.updateHabits(list.mapIndexed { index, h -> h.copy(position = index, updatedAt = t) })
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

    /**
     * Migración de bullet journal: la tarea queda como ">" con la marca de adónde fue y se
     * crea una copia abierta en [toDate]. Si ya estaba migrada a ese día, no duplica.
     */
    suspend fun migrateTask(entryId: Long, toDate: LocalDate) = db.withTransaction {
        val entry = dao.getEntry(entryId) ?: return@withTransaction
        if (entry.type != EntryType.TASK) return@withTransaction
        val to = toDate.toEpochDay()
        if (entry.migratedTo == to) return@withTransaction
        val t = now()
        dao.updateEntry(entry.copy(status = TaskStatus.MIGRATED, migratedTo = to, updatedAt = t))
        dao.insertEntry(
            EntryEntity(
                epochDay = to,
                type = EntryType.TASK,
                text = entry.text,
                position = dao.nextEntryPosition(to),
                createdAt = t,
                updatedAt = t,
            ),
        )
        markChanged(entry.epochDay, t)
        markChanged(to, t)
    }

    suspend fun deleteEntry(entryId: Long) = db.withTransaction {
        val entry = dao.getEntry(entryId) ?: return@withTransaction
        val t = now()
        // Borrado blando: desaparece de la app pero el borrado se sincroniza.
        dao.updateEntry(entry.copy(deleted = true, updatedAt = t))
        markChanged(entry.epochDay, t)
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

    /** Tiempo del día (null = quitar). Cuenta como cambio: el día vuelve a pendiente. */
    suspend fun setWeather(date: LocalDate, weather: Weather?) = setTrackers(date) { it.copy(weather = weather?.key) }

    /** Ánimo del día (null = quitar). */
    suspend fun setMood(date: LocalDate, mood: Mood?) = setTrackers(date) { it.copy(mood = mood?.level) }

    private suspend fun setTrackers(date: LocalDate, change: (DayEntity) -> DayEntity) = db.withTransaction {
        val d = date.toEpochDay()
        val day = dao.getDay(d) ?: DayEntity(epochDay = d)
        val changed = change(day)
        if (changed == day) return@withTransaction
        val t = now()
        dao.upsertDay(changed.copy(trackersUpdatedAt = t, transcribed = false, updatedAt = t))
    }

    suspend fun setTranscribed(date: LocalDate, transcribed: Boolean) = db.withTransaction {
        val d = date.toEpochDay()
        val day = dao.getDay(d) ?: DayEntity(epochDay = d)
        dao.upsertDay(
            if (transcribed) {
                val t = now()
                day.copy(transcribed = true, lastTranscribedAt = t, updatedAt = t)
            } else {
                day.copy(transcribed = false, updatedAt = now())
            },
        )
    }

    /** Guarda lo importado de golpe; los hábitos reconocidos se marcan como hechos. */
    suspend fun importItems(items: List<ImportItem>) = db.withTransaction {
        val t = now()
        items.groupBy { it.date }.forEach { (date, list) ->
            val d = date.toEpochDay()
            var position = dao.nextEntryPosition(d)
            list.forEach { item ->
                val habitId = item.habitId
                if (habitId != null) {
                    dao.upsertHabitCheck(HabitCheckEntity(d, habitId, done = true, updatedAt = t))
                } else {
                    dao.insertEntry(
                        EntryEntity(
                            epochDay = d,
                            type = item.type,
                            status = if (item.type == EntryType.TASK) item.status else TaskStatus.OPEN,
                            text = item.text,
                            position = position++,
                            createdAt = t,
                            updatedAt = t,
                        ),
                    )
                }
            }
            markChanged(d, t)
        }
    }

    // ---- Entradas por mes (no afectan a "pasado al cuaderno") ----

    fun observeMonthEntries(from: YearMonth, to: YearMonth): Flow<List<MonthEntryEntity>> =
        dao.observeMonthEntries(from.toYm(), to.toYm())

    suspend fun addMonthEntry(month: YearMonth, type: EntryType, text: String) = db.withTransaction {
        val ym = month.toYm()
        val t = now()
        dao.insertMonthEntry(
            MonthEntryEntity(ym = ym, type = type, text = text, position = dao.nextMonthPosition(ym), createdAt = t, updatedAt = t),
        )
    }

    suspend fun cycleMonthEntry(id: Long) = db.withTransaction {
        val e = dao.getMonthEntry(id) ?: return@withTransaction
        if (e.type != EntryType.TASK) return@withTransaction
        dao.updateMonthEntry(e.copy(status = e.status.next(), updatedAt = now()))
    }

    suspend fun updateMonthEntry(id: Long, type: EntryType, status: TaskStatus, text: String) = db.withTransaction {
        val e = dao.getMonthEntry(id) ?: return@withTransaction
        val newStatus = if (type == EntryType.TASK) status else TaskStatus.OPEN
        dao.updateMonthEntry(e.copy(type = type, status = newStatus, text = text, updatedAt = now()))
    }

    suspend fun deleteMonthEntry(id: Long) = db.withTransaction {
        val e = dao.getMonthEntry(id) ?: return@withTransaction
        dao.updateMonthEntry(e.copy(deleted = true, updatedAt = now()))
    }

    /** Pasa una entrada a otro mes: queda como ">" con la marca y se copia abierta allí. */
    suspend fun migrateMonthEntry(id: Long, to: YearMonth) = db.withTransaction {
        val e = dao.getMonthEntry(id) ?: return@withTransaction
        val ym = to.toYm()
        if (e.migratedTo == ym) return@withTransaction
        val t = now()
        dao.updateMonthEntry(
            e.copy(
                status = if (e.type == EntryType.TASK) TaskStatus.MIGRATED else e.status,
                migratedTo = ym,
                updatedAt = t,
            ),
        )
        dao.insertMonthEntry(
            MonthEntryEntity(ym = ym, type = e.type, text = e.text, position = dao.nextMonthPosition(ym), createdAt = t, updatedAt = t),
        )
    }

    private suspend fun markChanged(epochDay: Long, t: Long) {
        val day = dao.getDay(epochDay) ?: DayEntity(epochDay = epochDay)
        dao.upsertDay(day.copy(transcribed = false, updatedAt = t))
    }
}

/** YearMonth ↔ entero aaaamm (como se guarda en la base de datos y en la nube). */
fun YearMonth.toYm(): Int = year * 100 + monthValue

fun ymToYearMonth(ym: Int): YearMonth = YearMonth.of(ym / 100, ym % 100)
