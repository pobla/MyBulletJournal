package com.pobla.cuaderno.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val SPANISH: Locale = Locale.forLanguageTag("es-ES")

private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", SPANISH)
private val longDateFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)
private val timestampFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", SPANISH)

fun LocalDate.weekdayName(): String =
    format(weekdayFormat).replaceFirstChar { it.titlecase(SPANISH) }

fun LocalDate.longDate(): String = format(longDateFormat)

fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timestampFormat)

private val dayMonthFormat = DateTimeFormatter.ofPattern("d 'de' MMMM", SPANISH)
private val shortDayMonthFormat = DateTimeFormatter.ofPattern("d MMM", SPANISH)

/** "Lunes 16 de noviembre" */
fun LocalDate.weekdayDayMonth(): String = "${weekdayName()} ${format(dayMonthFormat)}"

/** Inicial del día de la semana en español: L M X J V S D. */
fun LocalDate.weekdayLetter(): String = "LMXJVSD"[dayOfWeek.value - 1].toString()

/** Lunes de la semana de esta fecha. */
fun LocalDate.startOfWeek(): LocalDate = minusDays((dayOfWeek.value - 1).toLong())

/** "16 – 22 de noviembre" o "28 oct – 3 nov". */
fun weekRangeLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    return if (start.month == end.month) {
        "${start.dayOfMonth} – ${end.format(dayMonthFormat)}"
    } else {
        "${start.format(shortDayMonthFormat)} – ${end.format(shortDayMonthFormat)}"
    }
}
