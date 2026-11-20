package com.pobla.cuaderno.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pobla.cuaderno.data.ALL_DAYS
import com.pobla.cuaderno.data.AppSettings
import com.pobla.cuaderno.data.ReminderKind
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Programa cada recordatorio como un trabajo único de WorkManager para la próxima hora indicada.
 * Al ejecutarse, el worker programa el del día siguiente (así no se acumula deriva).
 */
class ReminderScheduler(
    context: Context,
    private val settings: AppSettings,
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Al abrir la app: se asegura de que los recordatorios activos estén programados,
     * sin tocar los que ya lo están (KEEP).
     */
    fun ensureScheduled() {
        ReminderKind.entries.forEach { sync(it, ExistingWorkPolicy.KEEP) }
    }

    /** Tras cambiar un ajuste: reprograma con la hora nueva. */
    fun reschedule(kind: ReminderKind) = sync(kind, ExistingWorkPolicy.REPLACE)

    /** Desde el propio worker: encola el de mañana detrás del actual sin cancelarlo. */
    fun scheduleNextFromWorker(kind: ReminderKind) = sync(kind, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private fun sync(kind: ReminderKind, policy: ExistingWorkPolicy) {
        val setting = settings.reminder(kind)
        if (!setting.enabled) {
            workManager.cancelUniqueWork(workName(kind))
            return
        }
        val delay = delayUntilNext(LocalDateTime.now(), setting.minutesOfDay, setting.days)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(workName(kind), policy, request)
    }

    companion object {
        private const val TAG = "recordatorios"

        fun workName(kind: ReminderKind) = "recordatorio_${kind.name.lowercase()}"

        /**
         * Tiempo hasta la próxima vez que el reloj marque [minutesOfDay] en uno de los [days]
         * (hoy si aún no ha pasado la hora, o el siguiente día permitido).
         */
        fun delayUntilNext(
            now: LocalDateTime,
            minutesOfDay: Int,
            days: Set<DayOfWeek> = ALL_DAYS,
        ): Duration {
            val allowed = days.ifEmpty { ALL_DAYS }
            val today = now.toLocalDate().atStartOfDay().plusMinutes(minutesOfDay.toLong())
            for (offset in 0L..7L) {
                val candidate = today.plusDays(offset)
                // Margen de un minuto para no disparar dos veces el mismo día al reprogramar.
                if (candidate.isAfter(now.plusMinutes(1)) && candidate.dayOfWeek in allowed) {
                    return Duration.between(now, candidate)
                }
            }
            return Duration.ofDays(1)
        }
    }
}
