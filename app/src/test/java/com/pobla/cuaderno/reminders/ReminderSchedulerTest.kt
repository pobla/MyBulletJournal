package com.pobla.cuaderno.reminders

import org.junit.Assert.assertEquals
import com.pobla.cuaderno.data.WORKDAYS
import com.pobla.cuaderno.data.daysFromMask
import com.pobla.cuaderno.data.toMask
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

    @Test
    fun `de lunes a viernes, un viernes por la tarde salta al lunes`() {
        // 20/11/2026 es viernes; aviso L–V a las 9:00
        val now = LocalDateTime.of(2026, 11, 20, 18, 0)
        val delay = ReminderScheduler.delayUntilNext(now, 9 * 60, WORKDAYS)
        assertEquals(LocalDateTime.of(2026, 11, 23, 9, 0), now.plus(delay))
    }

    @Test
    fun `de lunes a viernes, un martes temprano es ese mismo dia`() {
        val now = LocalDateTime.of(2026, 11, 24, 7, 30)
        val delay = ReminderScheduler.delayUntilNext(now, 9 * 60, WORKDAYS)
        assertEquals(LocalDateTime.of(2026, 11, 24, 9, 0), now.plus(delay))
    }

    @Test
    fun `la mascara de dias ida y vuelta`() {
        assertEquals(WORKDAYS, daysFromMask(WORKDAYS.toMask()))
        assertEquals(31, WORKDAYS.toMask())
    }
}
