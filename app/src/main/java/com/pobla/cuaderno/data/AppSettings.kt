package com.pobla.cuaderno.data

import android.content.Context
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReminderKind(val title: String, val defaultMinutes: Int) {
    HABITS("Recordatorio de hábitos", 21 * 60),
    TRANSCRIBE("Pasar al cuaderno", 21 * 60 + 30),
}

val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.values().toSet()
val WORKDAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)
val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

data class ReminderSetting(
    val enabled: Boolean,
    val minutesOfDay: Int,
    /** Días de la semana en los que se avisa. Nunca vacío. */
    val days: Set<DayOfWeek> = ALL_DAYS,
) {
    val hour: Int get() = minutesOfDay / 60
    val minute: Int get() = minutesOfDay % 60
    val label: String get() = "%02d:%02d".format(hour, minute)

    val daysLabel: String
        get() = when (days) {
            ALL_DAYS -> "Todos los días"
            WORKDAYS -> "De lunes a viernes"
            WEEKEND -> "Fines de semana"
            else -> days.sorted().joinToString(", ") { DAY_NAMES[it.value - 1] }
        }

    private companion object {
        val DAY_NAMES = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    }
}

/** Días como máscara de bits (lunes = bit 0) para guardarlos en preferencias. */
fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

fun daysFromMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet().ifEmpty { ALL_DAYS }

/** Preferencias sencillas en SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))

    /** Usar los colores del fondo de pantalla (Android 12+). */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    private val _reminders = MutableStateFlow(
        ReminderKind.entries.associateWith { readReminder(it) },
    )

    /** Ajustes de los recordatorios (activado y hora). */
    val reminders: StateFlow<Map<ReminderKind, ReminderSetting>> = _reminders.asStateFlow()

    fun reminder(kind: ReminderKind): ReminderSetting = _reminders.value.getValue(kind)

    fun setReminder(kind: ReminderKind, setting: ReminderSetting) {
        prefs.edit()
            .putBoolean("reminder_${kind.name}_enabled", setting.enabled)
            .putInt("reminder_${kind.name}_minutes", setting.minutesOfDay)
            .putInt("reminder_${kind.name}_days", setting.days.toMask())
            .apply()
        _reminders.value = _reminders.value + (kind to setting)
    }

    private fun readReminder(kind: ReminderKind) = ReminderSetting(
        enabled = prefs.getBoolean("reminder_${kind.name}_enabled", false),
        minutesOfDay = prefs.getInt("reminder_${kind.name}_minutes", kind.defaultMinutes),
        days = daysFromMask(prefs.getInt("reminder_${kind.name}_days", ALL_DAYS.toMask())),
    )

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
}
