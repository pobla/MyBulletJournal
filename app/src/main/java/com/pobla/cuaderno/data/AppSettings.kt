package com.pobla.cuaderno.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
}
