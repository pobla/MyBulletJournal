package com.pobla.cuaderno.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    private val tsv = """
        300	S	Importar texto
        300	B	Recibe texto con Compartir
        300	B	Vista previa
        200	S	Traducción al inglés
        100	S	Fase 4: recordatorios
        100	B	Permiso de notificaciones
        línea rota
    """.trimIndent()

    @Test
    fun `agrupa los puntos bajo su titulo`() {
        val notes = ReleaseNotes.parse(tsv)
        assertEquals(3, notes.size)
        assertEquals("Importar texto", notes[0].title)
        assertEquals(listOf("Recibe texto con Compartir", "Vista previa"), notes[0].bullets)
        assertEquals(emptyList<String>(), notes[1].bullets)
    }

    @Test
    fun `filtra lo nuevo desde la version instalada`() {
        val notes = ReleaseNotes.parse(tsv)
        assertEquals(listOf(300L, 200L), ReleaseNotes.between(notes, afterTime = 100).map { it.time })
        assertEquals(listOf(200L), ReleaseNotes.between(notes, afterTime = 100, upToTime = 200).map { it.time })
    }
}
