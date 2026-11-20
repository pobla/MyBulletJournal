package com.pobla.cuaderno.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pobla.cuaderno.MainActivity
import com.pobla.cuaderno.R
import com.pobla.cuaderno.util.weekdayDayMonth
import java.time.LocalDate

object Notifications {
    private const val CHANNEL_HABITS = "habitos"
    private const val CHANNEL_TRANSCRIBE = "cuaderno"
    private const val ID_HABITS = 1001
    private const val ID_TRANSCRIBE = 1002

    const val EXTRA_TAB = "abrir_pestana"
    const val TAB_DAY = "day"
    const val TAB_PENDING = "pending"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_HABITS, "Hábitos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Recordatorio diario de los hábitos sin marcar"
                },
                NotificationChannel(CHANNEL_TRANSCRIBE, "Pasar al cuaderno", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Aviso de días pendientes de pasar al bullet journal"
                },
            ),
        )
    }

    /** ¿Podemos mostrar notificaciones ahora mismo? */
    fun canNotify(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showHabits(context: Context, missing: List<String>) {
        val text = if (missing.size == 1) "Te falta: ${missing.first()}" else "Te faltan: ${missing.joinToString(", ")}"
        notify(
            context = context,
            id = ID_HABITS,
            channel = CHANNEL_HABITS,
            title = "¿Has hecho tus hábitos?",
            text = text,
            tab = TAB_DAY,
        )
    }

    fun showTranscribe(context: Context, dates: List<LocalDate>) {
        val title = if (dates.size == 1) "1 día por pasar al cuaderno" else "${dates.size} días por pasar al cuaderno"
        val lines = dates.take(6).map { it.weekdayDayMonth() }
        val text = lines.joinToString(" · ") + if (dates.size > lines.size) " y ${dates.size - lines.size} más" else ""
        notify(
            context = context,
            id = ID_TRANSCRIBE,
            channel = CHANNEL_TRANSCRIBE,
            title = title,
            text = text,
            tab = TAB_PENDING,
        )
    }

    /** Aviso de prueba desde Ajustes, con datos de ejemplo. */
    fun showTest(context: Context, kind: com.pobla.cuaderno.data.ReminderKind) {
        when (kind) {
            com.pobla.cuaderno.data.ReminderKind.HABITS ->
                showHabits(context, listOf("(prueba) Creatina", "Duolingo"))
            com.pobla.cuaderno.data.ReminderKind.TRANSCRIBE ->
                showTranscribe(context, listOf(LocalDate.now().minusDays(1), LocalDate.now()))
        }
    }

    private fun notify(context: Context, id: Int, channel: String, title: String, text: String, tab: String) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_TAB, tab)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permiso retirado entre la comprobación y el aviso: no hacemos nada.
        }
    }
}
