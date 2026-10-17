package com.omglabs.til

import android.graphics.Color

/**
 * Quick-pick colours, chosen to behave as wallpaper rather than as UI.
 *
 * Backdrops are deep but not pure black (hairline serifs crush on OLED) or soft paper tones.
 * Inks are warm off-whites and muted accents; saturated colour at 20% of the screen height
 * reads as a toy, so nothing here is at full chroma.
 */
object Palette {

    /** Slightly warm off-white reads richer than #FFFFFF at this size. */
    const val DEFAULT_INK = 0xFFF2EDE4.toInt()

    /** Not pure black, so the serif hairlines don't crush on OLED. */
    const val DEFAULT_BACKDROP = 0xFF0A0A0A.toInt()

    data class Swatch(val label: String, val color: Int)

    val INKS = listOf(
        Swatch("Warm white", DEFAULT_INK),
        Swatch("Cool white", 0xFFFFFFFF.toInt()),
        Swatch("Muted gold", 0xFFC8A961.toInt()),
        Swatch("Dusty rose", 0xFFE3B5A4.toInt()),
        Swatch("Sage", 0xFFA8BBA2.toInt()),
        Swatch("Pale sky", 0xFF9FB6CD.toInt()),
        Swatch("Terracotta", 0xFFC8755B.toInt()),
        Swatch("Lilac", 0xFFBDB0D0.toInt()),
        Swatch("Graphite", 0xFF3A3A3A.toInt()),
        Swatch("Ink black", 0xFF0A0A0A.toInt())
    )

    val BACKDROPS = listOf(
        Swatch("Ink black", DEFAULT_BACKDROP),
        Swatch("Charcoal", 0xFF1C1C1E.toInt()),
        Swatch("Slate", 0xFF1C2228.toInt()),
        Swatch("Deep navy", 0xFF0B1120.toInt()),
        Swatch("Forest", 0xFF0C1A14.toInt()),
        Swatch("Oxblood", 0xFF1A0B0E.toInt()),
        Swatch("Plum", 0xFF140B1A.toInt()),
        Swatch("Espresso", 0xFF14100C.toInt()),
        Swatch("Cream", 0xFFEDE6D8.toInt()),
        Swatch("Paper", 0xFFF4F1EA.toInt())
    )

    /**
     * WCAG relative-luminance contrast ratio, 1.0 (identical) to 21.0 (black on white).
     * Used only to warn before someone sets an invisible wallpaper.
     */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    /** `#RRGGBB`, the form the hex field in the picker shows. */
    fun toHex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)
}
