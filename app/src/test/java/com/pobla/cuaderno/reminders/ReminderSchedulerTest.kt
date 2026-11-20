package com.pobla.cuaderno.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ReminderSchedulerTest {

    private val nine = 21 * 60

    @Test
    fun `si aun no ha llegado la hora, es hoy`() {
        val now = LocalDateTime.of(2026, 11, 20, 18, 30)
        assertEquals(Duration.ofMinutes(150), ReminderScheduler.delayUntilNext(now, nine))
    }

    @Test
    fun `si ya paso la hora, es manana`() {
        val now = LocalDateTime.of(2026, 11, 20, 22, 0)
        assertEquals(Duration.ofHours(23), ReminderScheduler.delayUntilNext(now, nine))
    }

    @Test
    fun `justo a la hora (al reprogramar desde el worker) pasa a manana`() {
        val now = LocalDateTime.of(2026, 11, 20, 21, 0, 20)
        val delay = ReminderScheduler.delayUntilNext(now, nine)
        assertEquals(LocalDateTime.of(2026, 11, 21, 21, 0), now.plus(delay))
    }
}
