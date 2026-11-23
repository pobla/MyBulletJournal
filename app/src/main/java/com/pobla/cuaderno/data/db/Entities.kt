package com.pobla.cuaderno.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pobla.cuaderno.model.EntryType
import com.pobla.cuaderno.model.TaskStatus

/**
 * Datos propios de un día. Las fechas se guardan como epochDay (LocalDate.toEpochDay()).
 *
 * - [transcribed]: está marcado como "pasado al cuaderno".
 * - [lastTranscribedAt]: última vez que se marcó; lo modificado después se resalta.
 */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val epochDay: Long,
    val summary: String = "",
    val summaryUpdatedAt: Long = 0,
    val transcribed: Boolean = false,
    val lastTranscribedAt: Long? = null,
    val updatedAt: Long = 0,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    val archived: Boolean = false,
    /** Para la sincronización con la nube (v2). */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(
    tableName = "habit_checks",
    primaryKeys = ["epochDay", "habitId"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class HabitCheckEntity(
    val epochDay: Long,
    val habitId: Long,
    val done: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "entries", indices = [Index("epochDay")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val type: EntryType,
    val status: TaskStatus = TaskStatus.OPEN,
    val text: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** Borrado "blando" para que el borrado también llegue a la nube (v2). */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

/** Foto adjunta a un día ([entryId] nulo) o a una entrada concreta. Se usará en la fase 3. */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("epochDay"), Index("entryId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val entryId: Long?,
    val fileName: String,
    val createdAt: Long,
)

/** Fila de la consulta de días pendientes (no es una tabla). */
data class PendingDayRow(
    val epochDay: Long,
    val summary: String,
    val lastTranscribedAt: Long?,
    val entryCount: Int,
    val habitCount: Int,
)
