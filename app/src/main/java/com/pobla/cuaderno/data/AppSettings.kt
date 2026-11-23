package com.pobla.cuaderno.data

import android.content.Context
import androidx.annotation.StringRes
import com.pobla.cuaderno.R
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReminderKind(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val defaultMinutes: Int,
) {
    HABITS(R.string.reminder_habits_title, R.string.reminder_habits_desc, 21 * 60),
    TRANSCRIBE(R.string.reminder_transcribe_title, R.string.reminder_transcribe_desc, 21 * 60 + 30),
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

    /** Última versión abierta (para mostrar "Novedades" una sola vez tras actualizar). */
    var lastSeenVersionCode: Int
        get() = prefs.getInt("last_seen_version", 0)
        set(value) = prefs.edit().putInt("last_seen_version", value).apply()

    var lastSeenCommitTime: Long
        get() = prefs.getLong("last_seen_commit_time", 0L)
        set(value) = prefs.edit().putLong("last_seen_commit_time", value).apply()

    // Copia en la nube
    /** Marca de tiempo hasta la que ya se subió todo (-1 = nada todavía). */
    var syncWatermark: Long
        get() = prefs.getLong("sync_watermark", -1L)
        set(value) = prefs.edit().putLong("sync_watermark", value).apply()

    /** Cuenta con la que se hizo la última copia (si cambia, se vuelve a subir todo). */
    var syncUid: String?
        get() = prefs.getString("sync_uid", null)
        set(value) = prefs.edit().putString("sync_uid", value).apply()

    private val _lastSyncAt = MutableStateFlow(prefs.getLong("last_sync_at", 0L))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    fun setLastSyncAt(value: Long) {
        prefs.edit().putLong("last_sync_at", value).apply()
        _lastSyncAt.value = value
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
}
