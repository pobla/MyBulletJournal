package com.pobla.cuaderno.util

import android.text.format.DateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Los formatos se crean con el idioma actual (inglés o español) en cada llamada,
// para que un cambio de idioma se note sin reiniciar la app.
private val locale: Locale get() = Locale.getDefault()

/** Formato adecuado al idioma a partir de un "esqueleto" (p. ej. "dMMMM" → "20 de noviembre" / "November 20"). */
private fun best(skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

private fun String.capitalized(): String = replaceFirstChar { it.titlecase(locale) }

/** "Viernes" / "Friday" */
fun LocalDate.weekdayName(): String = dayOfWeek.getDisplayName(TextStyle.FULL, locale).capitalized()

/** "20 de noviembre de 2026" / "November 20, 2026" */
fun LocalDate.longDate(): String = format(best("dMMMMyyyy"))

fun formatTimestamp(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(best("dMMMHHmm"))

/** "Lunes 16 de noviembre" / "Monday, November 16" */
fun LocalDate.weekdayDayMonth(): String = format(best("EEEEdMMMM")).capitalized()

/** "Noviembre de 2026" / "November 2026" */
fun java.time.YearMonth.monthYear(): String = atDay(1).format(best("MMMMyyyy")).capitalized()

/** "Noviembre" / "November" */
fun java.time.YearMonth.monthName(): String = atDay(1).format(best("MMMM")).capitalized()

/** "24 nov" / "Nov 24" */
fun LocalDate.shortDayMonth(): String = format(best("dMMM"))

/** Inicial del día: L M X J V S D / M T W T F S S. */
fun LocalDate.weekdayLetter(): String = dayOfWeek.letter()

fun DayOfWeek.letter(): String = getDisplayName(TextStyle.NARROW, locale).uppercase(locale)

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, locale)

/** Lunes de la semana de esta fecha. */
fun LocalDate.startOfWeek(): LocalDate = minusDays((dayOfWeek.value - 1).toLong())

/** "16 nov – 22 nov" / "Nov 16 – Nov 22" */
fun weekRangeLabel(start: LocalDate): String {
    val f = best("dMMM")
    return "${start.format(f)} – ${start.plusDays(6).format(f)}"
}
