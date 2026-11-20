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
