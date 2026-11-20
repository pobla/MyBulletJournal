package com.pobla.cuaderno.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ImportParserTest {
    // Viernes 20/11/2026
    private val today = LocalDate.of(2026, 11, 20)

    private fun parse(text: String) = ImportParser.parse(text, today, today)

    @Test
    fun `simbolos de bullet journal`() {
        val items = parse(
            """
            Aquí tienes tu lista:
            • Comprar pan
            ○ Dentista a las 10
            – Idea para el regalo de Ollie
            × Llamar al banco
            > Revisar factura
            """.trimIndent(),
        )
        assertEquals(5, items.size) // la frase de introducción se ignora
        assertEquals(EntryType.TASK, items[0].type)
        assertEquals("Comprar pan", items[0].text)
        assertEquals(EntryType.EVENT, items[1].type)
        assertEquals(EntryType.NOTE, items[2].type)
        assertEquals(TaskStatus.DONE, items[3].status)
        assertEquals(TaskStatus.MIGRATED, items[4].status)
    }

    @Test
    fun `cabeceras de fecha cambian el dia`() {
        val items = parse(
            """
            ## Hoy
            • Pan
            **Mañana:**
            ○ Dentista
            Lunes
            • Gimnasio
            2026-12-01
            – Nota
            """.trimIndent(),
        )
        assertEquals(today, items[0].date)
        assertEquals(today.plusDays(1), items[1].date)
        assertEquals(LocalDate.of(2026, 11, 23), items[2].date)
        assertEquals(LocalDate.of(2026, 12, 1), items[3].date)
    }

    @Test
    fun `markdown y casillas`() {
        val items = parse("- [ ] Pagar luz\n- [x] **Creatina**\n1. Llamar a mamá")
        assertEquals(TaskStatus.OPEN, items[0].status)
        assertEquals(TaskStatus.DONE, items[1].status)
        assertEquals("Creatina", items[1].text)
        assertEquals("Llamar a mamá", items[2].text)
    }

    @Test
    fun `sin vinetas cada linea es una tarea`() {
        val items = parse("Comprar pan\nLlamar a mamá")
        assertEquals(2, items.size)
        assertEquals(EntryType.TASK, items[1].type)
    }

    @Test
    fun `fechas en ingles y dia-mes`() {
        assertEquals(today.plusDays(1), ImportParser.parseDateHeader("Tomorrow", today))
        assertEquals(LocalDate.of(2026, 11, 25), ImportParser.parseDateHeader("Wednesday", today))
        assertEquals(LocalDate.of(2026, 11, 23), ImportParser.parseDateHeader("23/11", today))
        assertEquals(today, ImportParser.parseDateHeader("viernes", today))
        assertNull(ImportParser.parseDateHeader("Comprar pan", today))
    }
}
