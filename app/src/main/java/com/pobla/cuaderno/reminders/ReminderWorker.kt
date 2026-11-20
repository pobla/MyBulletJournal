package com.pobla.cuaderno.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pobla.cuaderno.CuadernoApp
import com.pobla.cuaderno.data.ReminderKind
import java.time.LocalDate

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND)
            ?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val container = (applicationContext as CuadernoApp).container
        val repository = container.repository

        if (container.settings.reminder(kind).enabled) {
            when (kind) {
                ReminderKind.HABITS -> {
                    val missing = repository.getMissingHabits(LocalDate.now())
                    if (missing.isNotEmpty()) Notifications.showHabits(applicationContext, missing)
                }
                ReminderKind.TRANSCRIBE -> {
                    val pending = repository.getPendingDays()
                    if (pending.isNotEmpty()) {
                        val dates = pending.map { LocalDate.ofEpochDay(it.epochDay) }
                        Notifications.showTranscribe(applicationContext, dates)
                    }
                }
            }
        }
        // Siguiente aviso: mañana a la misma hora (o nada si se desactivó).
        container.reminders.scheduleNextFromWorker(kind)
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}
