package com.pobla.cuaderno.update

import android.content.Context

/** Un cambio de las notas de versión: título del commit y sus puntos. */
data class ReleaseNote(val time: Long, val title: String, val bullets: List<String>)

/**
 * Notas de versión generadas por CI a partir de los commits (`changelog.tsv`):
 * cada línea es `epoch \t S|B \t texto` (S = título, B = punto del cambio anterior).
 */
object ReleaseNotes {
    private const val ASSET = "changelog.tsv"

    fun parse(tsv: String): List<ReleaseNote> {
        val notes = mutableListOf<ReleaseNote>()
        for (line in tsv.lineSequence()) {
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3) continue
            val time = parts[0].toLongOrNull() ?: continue
            val text = parts[2].trim()
            if (text.isEmpty()) continue
            when (parts[1]) {
                "S" -> notes += ReleaseNote(time, text, emptyList())
                "B" -> if (notes.isNotEmpty()) {
                    val last = notes.removeAt(notes.lastIndex)
                    notes += last.copy(bullets = last.bullets + text)
                }
            }
        }
        return notes.sortedByDescending { it.time }
    }

    /** Cambios posteriores a [afterTime] y hasta [upToTime] (incluido), del más nuevo al más viejo. */
    fun between(notes: List<ReleaseNote>, afterTime: Long, upToTime: Long = Long.MAX_VALUE): List<ReleaseNote> =
        notes.filter { it.time > afterTime && it.time <= upToTime }

    /** Las notas que viajan dentro del APK (vacío si se compiló fuera de CI). */
    fun bundled(context: Context): List<ReleaseNote> = try {
        context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
    } catch (_: Exception) {
        emptyList()
    }
}
