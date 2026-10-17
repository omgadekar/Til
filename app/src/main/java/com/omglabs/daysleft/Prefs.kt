package com.omglabs.daysleft

import android.content.Context
import java.time.LocalDate

/**
 * Everything the painter needs, read in one go so a render can never see a half-changed
 * set of preferences.
 *
 * [targetDate] is null for the original behaviour: count to 1 January and roll over on its own
 * every year. A chosen date is fixed and stops at zero once it arrives.
 */
data class WallpaperSettings(
    val styleIndex: Int,
    val inkColor: Int,
    val backdropColor: Int,
    val caption: String,
    val targetDate: LocalDate?
)

object Prefs {
    private const val FILE = "daysleft"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_STYLE = "style"
    private const val KEY_INK = "ink_color"
    private const val KEY_BACKDROP = "backdrop_color"
    private const val KEY_CAPTION = "caption"
    private const val KEY_TARGET = "target_epoch_day"

    /** Longest caption that still leaves the number the hero of the composition. */
    const val CAPTION_MAX_LENGTH = 40

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun styleIndex(context: Context): Int =
        prefs(context).getInt(KEY_STYLE, 0)

    fun setStyleIndex(context: Context, index: Int) =
        prefs(context).edit().putInt(KEY_STYLE, index).apply()

    fun inkColor(context: Context): Int =
        prefs(context).getInt(KEY_INK, Palette.DEFAULT_INK)

    fun setInkColor(context: Context, color: Int) =
        prefs(context).edit().putInt(KEY_INK, color).apply()

    fun backdropColor(context: Context): Int =
        prefs(context).getInt(KEY_BACKDROP, Palette.DEFAULT_BACKDROP)

    fun setBackdropColor(context: Context, color: Int) =
        prefs(context).edit().putInt(KEY_BACKDROP, color).apply()

    fun caption(context: Context): String =
        prefs(context).getString(KEY_CAPTION, "").orEmpty()

    fun setCaption(context: Context, caption: String) =
        prefs(context).edit()
            .putString(KEY_CAPTION, caption.trim().take(CAPTION_MAX_LENGTH))
            .apply()

    /** null means "1 January of next year, recalculated every day". */
    fun targetDate(context: Context): LocalDate? {
        val day = prefs(context).getLong(KEY_TARGET, NO_TARGET)
        return if (day == NO_TARGET) null else LocalDate.ofEpochDay(day)
    }

    fun setTargetDate(context: Context, date: LocalDate?) =
        prefs(context).edit()
            .putLong(KEY_TARGET, date?.toEpochDay() ?: NO_TARGET)
            .apply()

    fun settings(context: Context) = WallpaperSettings(
        styleIndex = styleIndex(context),
        inkColor = inkColor(context),
        backdropColor = backdropColor(context),
        caption = caption(context),
        targetDate = targetDate(context)
    )

    private const val NO_TARGET = Long.MIN_VALUE

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
