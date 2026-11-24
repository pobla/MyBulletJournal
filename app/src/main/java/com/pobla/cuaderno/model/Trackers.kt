package com.pobla.cuaderno.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.pobla.cuaderno.R

/** Tiempo del día: símbolos de línea pensados para dibujarlos luego en el cuaderno. */
enum class Weather(val key: String, @StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    SUN("sun", R.string.wx_sun, R.drawable.ic_wx_sun),
    PARTLY("partly", R.string.wx_partly, R.drawable.ic_wx_partly),
    CLOUD("cloud", R.string.wx_cloud, R.drawable.ic_wx_cloud),
    RAIN("rain", R.string.wx_rain, R.drawable.ic_wx_rain),
    STORM("storm", R.string.wx_storm, R.drawable.ic_wx_storm),
    WIND("wind", R.string.wx_wind, R.drawable.ic_wx_wind),
    SNOW("snow", R.string.wx_snow, R.drawable.ic_wx_snow);

    companion object {
        fun fromKey(key: String?): Weather? = entries.firstOrNull { it.key == key }
    }
}

/** Ánimo del día: 5 caras con un color cada una (para el "tracker de píxeles" del mes). */
enum class Mood(val level: Int, @StringRes val labelRes: Int, @DrawableRes val iconRes: Int, val color: Long) {
    GREAT(5, R.string.mood_great, R.drawable.ic_mood_great, 0xFF4CAF7A),
    GOOD(4, R.string.mood_good, R.drawable.ic_mood_good, 0xFF9CCC65),
    OK(3, R.string.mood_ok, R.drawable.ic_mood_ok, 0xFFF2C14E),
    MEH(2, R.string.mood_meh, R.drawable.ic_mood_meh, 0xFFF29B4E),
    BAD(1, R.string.mood_bad, R.drawable.ic_mood_bad, 0xFFE06C6C);

    companion object {
        fun fromLevel(level: Int?): Mood? = entries.firstOrNull { it.level == level }
    }
}
