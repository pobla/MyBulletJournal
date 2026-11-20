package com.pobla.cuaderno.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReminderKind(val title: String, val defaultMinutes: Int) {
    HABITS("Recordatorio de hábitos", 21 * 60),
    TRANSCRIBE("Pasar al cuaderno", 21 * 60 + 30),
}

data class ReminderSetting(val enabled: Boolean, val minutesOfDay: Int) {
    val hour: Int get() = minutesOfDay / 60
    val minute: Int get() = minutesOfDay % 60
    val label: String get() = "%02d:%02d".format(hour, minute)
}

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
            .apply()
        _reminders.value = _reminders.value + (kind to setting)
    }

    private fun readReminder(kind: ReminderKind) = ReminderSetting(
        enabled = prefs.getBoolean("reminder_${kind.name}_enabled", false),
        minutesOfDay = prefs.getInt("reminder_${kind.name}_minutes", kind.defaultMinutes),
    )

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
}
