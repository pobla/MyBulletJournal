package com.pobla.cuaderno.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalTest {

    @Test
    fun `atajo o espacio crea evento`() {
        assertEquals(EntryShortcuts.Match(EntryType.EVENT, "Cena con Ollie"), EntryShortcuts.match("o Cena con Ollie"))
        assertEquals(EntryType.EVENT, EntryShortcuts.match("O ")?.type)
    }

    @Test
    fun `atajo guion espacio crea nota`() {
        assertEquals(EntryShortcuts.Match(EntryType.NOTE, "idea"), EntryShortcuts.match("- idea"))
        assertEquals(EntryType.NOTE, EntryShortcuts.match("– idea")?.type)
    }

    @Test
    fun `sin atajo no hay coincidencia`() {
        assertNull(EntryShortcuts.match("Comprar pan"))
        assertNull(EntryShortcuts.match("-5 kg"))
        assertNull(EntryShortcuts.match("oso"))
    }

    @Test
    fun `la tarea cicla abierta hecha migrada`() {
        assertEquals(TaskStatus.DONE, TaskStatus.OPEN.next())
        assertEquals(TaskStatus.MIGRATED, TaskStatus.DONE.next())
        assertEquals(TaskStatus.OPEN, TaskStatus.MIGRATED.next())
    }

    @Test
    fun `simbolos clasicos`() {
        assertEquals("•", symbolFor(EntryType.TASK, TaskStatus.OPEN))
        assertEquals("×", symbolFor(EntryType.TASK, TaskStatus.DONE))
        assertEquals(">", symbolFor(EntryType.TASK, TaskStatus.MIGRATED))
        assertEquals("○", symbolFor(EntryType.EVENT, TaskStatus.DONE))
        assertEquals("–", symbolFor(EntryType.NOTE, TaskStatus.OPEN))
    }
}
