package com.pobla.cuaderno.model

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Una línea reconocida al importar texto. [habitId] se rellena si coincide con un hábito. */
data class ImportItem(
    val date: LocalDate,
    val type: EntryType,
    val status: TaskStatus,
    val text: String,
    val habitId: Long? = null,
)

/**
 * Convierte texto libre (p. ej. una respuesta de Claude compartida a la app) en entradas.
 *
 * - Una línea por elemento: • tarea, ○ evento, – nota, × hecha, > migrada,
 *   y también "* ", "- [ ]", "- [x]", "1." …
 * - Una línea que sea solo una fecha ("Mañana", "Lunes", "23/11", "2026-11-23", "## Hoy")
 *   cambia el día de las líneas siguientes.
 * - Si el texto no tiene ninguna viñeta, cada línea es una tarea; si tiene, las líneas sin
 *   viñeta (saludos, explicaciones) se ignoran.
 */
object ImportParser {

    private data class Bullet(val type: EntryType, val status: TaskStatus, val text: String)

    fun parse(text: String, defaultDate: LocalDate, today: LocalDate): List<ImportItem> {
        val bullets = mutableListOf<ImportItem>()
        val plain = mutableListOf<ImportItem>()
        var current = defaultDate

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val bullet = parseBullet(line)
            if (bullet != null) {
                if (bullet.text.isNotBlank()) {
                    bullets += ImportItem(current, bullet.type, bullet.status, bullet.text)
                }
                continue
            }
            val date = parseDateHeader(line, today)
            if (date != null) {
                current = date
                continue
            }
            val clean = cleanText(line)
            if (clean.isNotBlank()) plain += ImportItem(current, EntryType.TASK, TaskStatus.OPEN, clean)
        }
        return bullets.ifEmpty { plain }
    }

    private val numbered = Regex("""^\d{1,2}[.)]\s+(.*)$""")
    private val checkbox = Regex("""^[-*•]?\s*\[([ xX✓])]\s+(.*)$""")

    private fun parseBullet(line: String): Bullet? {
        checkbox.matchEntire(line)?.let { m ->
            val done = m.groupValues[1].isNotBlank()
            return Bullet(EntryType.TASK, if (done) TaskStatus.DONE else TaskStatus.OPEN, cleanText(m.groupValues[2]))
        }
        numbered.matchEntire(line)?.let { m ->
            return Bullet(EntryType.TASK, TaskStatus.OPEN, cleanText(m.groupValues[1]))
        }
        val rules = listOf(
            listOf("•", "*", "·", "▪") to (EntryType.TASK to TaskStatus.OPEN),
            listOf("×", "✕", "✓", "✔", "x", "X") to (EntryType.TASK to TaskStatus.DONE),
            listOf(">", "→") to (EntryType.TASK to TaskStatus.MIGRATED),
            listOf("○", "◦", "◯", "o", "O") to (EntryType.EVENT to TaskStatus.OPEN),
            listOf("–", "—", "-") to (EntryType.NOTE to TaskStatus.OPEN),
        )
        for ((symbols, kind) in rules) {
            for (symbol in symbols) {
                if (line.startsWith("$symbol ")) {
                    return Bullet(kind.first, kind.second, cleanText(line.substring(symbol.length + 1)))
                }
            }
        }
        return null
    }

    /** Quita negritas/cursivas de Markdown y espacios sobrantes. */
    private fun cleanText(s: String): String =
        s.replace("**", "").replace("__", "").trim().trim('*', '_').trim()

    // ---- Fechas ----

    private val weekdays: Map<String, DayOfWeek> = buildMap {
        listOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
            .forEachIndexed { i, n -> put(n, DayOfWeek.of(i + 1)) }
        listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
            .forEachIndexed { i, n -> put(n, DayOfWeek.of(i + 1)) }
    }
    private val iso = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val dayMonth = Regex("""^(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?$""")
    private val weekdayWithNumber = Regex("""^([a-z]+)\s+(\d{1,2})$""")

    fun parseDateHeader(line: String, today: LocalDate): LocalDate? {
        if (line.length > 40) return null
        var s = normalize(line).trim('#', '*', '_', ':', ' ', '.', ',', '-')
        for (prefix in listOf("el ", "para el ", "para ", "on ", "for ")) {
            if (s.startsWith(prefix)) s = s.removePrefix(prefix)
        }
        s = s.trim()
        when (s) {
            "hoy", "today" -> return today
            "manana", "tomorrow" -> return today.plusDays(1)
            "pasado manana", "day after tomorrow" -> return today.plusDays(2)
            "ayer", "yesterday" -> return today.minusDays(1)
        }
        weekdays[s]?.let { return today.with(TemporalAdjusters.nextOrSame(it)) }
        weekdayWithNumber.matchEntire(s)?.let { m ->
            if (m.groupValues[1] in weekdays) return dayOfMonthNear(m.groupValues[2].toInt(), today)
        }
        iso.matchEntire(s)?.let { m ->
            return runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()
        }
        dayMonth.matchEntire(s)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearText = m.groupValues[3]
            val year = when {
                yearText.isEmpty() -> today.year
                yearText.length == 2 -> 2000 + yearText.toInt()
                else -> yearText.toInt()
            }
            return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        return null
    }

    /** "Lunes 23": el día 23 de este mes, o del siguiente si ya quedó muy atrás. */
    private fun dayOfMonthNear(day: Int, today: LocalDate): LocalDate? {
        val thisMonth = runCatching { today.withDayOfMonth(day) }.getOrNull()
        if (thisMonth != null && !thisMonth.isBefore(today.minusDays(7))) return thisMonth
        return runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
    }

    /** Minúsculas y sin tildes, para comparar ("Miércoles" == "miercoles"). */
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
