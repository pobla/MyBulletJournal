package com.pobla.cuaderno.model

/** Tipos de entrada del registro rápido de bullet journal. */
enum class EntryType(val symbol: String, val label: String) {
    TASK("•", "Tarea"),
    EVENT("○", "Evento"),
    NOTE("–", "Nota");

    fun next(): EntryType = entries[(ordinal + 1) % entries.size]
}

/** Estado de una tarea: • abierta → × hecha → > migrada → • abierta. */
enum class TaskStatus(val symbol: String, val label: String) {
    OPEN("•", "Pendiente"),
    DONE("×", "Hecha"),
    MIGRATED(">", "Migrada");

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
