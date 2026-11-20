package com.pobla.cuaderno.model

import androidx.annotation.StringRes
import com.pobla.cuaderno.R

/** Tipos de entrada del registro rápido de bullet journal. */
enum class EntryType(val symbol: String, @StringRes val labelRes: Int) {
    TASK("•", R.string.entry_task),
    EVENT("○", R.string.entry_event),
    NOTE("–", R.string.entry_note);

    fun next(): EntryType = entries[(ordinal + 1) % entries.size]
}

/** Estado de una tarea: • abierta → × hecha → > migrada → • abierta. */
enum class TaskStatus(val symbol: String, @StringRes val labelRes: Int) {
    OPEN("•", R.string.task_open),
    DONE("×", R.string.task_done),
    MIGRATED(">", R.string.task_migrated);

    fun next(): TaskStatus = when (this) {
        OPEN -> DONE
        DONE -> MIGRATED
        MIGRATED -> OPEN
    }
}

fun symbolFor(type: EntryType, status: TaskStatus): String =
    if (type == EntryType.TASK) status.symbol else type.symbol

/**
 * Atajos al escribir al principio de una entrada:
 * "o " → evento, "- " → nota, "* " / "• " → tarea.
 * Se acepta "O " porque el teclado suele poner mayúscula al empezar.
 */
object EntryShortcuts {
    data class Match(val type: EntryType, val rest: String)

    private val prefixes = listOf(
        "o " to EntryType.EVENT,
        "O " to EntryType.EVENT,
        "○ " to EntryType.EVENT,
        "- " to EntryType.NOTE,
        "– " to EntryType.NOTE,
        "— " to EntryType.NOTE,
        "* " to EntryType.TASK,
        "• " to EntryType.TASK,
    )

    fun match(input: String): Match? =
        prefixes.firstOrNull { input.startsWith(it.first) }
            ?.let { (prefix, type) -> Match(type, input.removePrefix(prefix)) }
}
