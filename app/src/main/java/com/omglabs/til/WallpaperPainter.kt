package com.omglabs.til

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.core.content.res.ResourcesCompat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

object WallpaperPainter {

    /** Digit ink height as a fraction of the wallpaper height. */
    private const val TARGET_HEIGHT = 0.18f

    /** Widest the number is allowed to get. */
    private const val MAX_WIDTH = 0.72f

    /** Caption text size, as a fraction of the number's ink height. */
    private const val CAPTION_RATIO = 0.17f

    /** Space between the caption and the top of the number, same fraction. */
    private const val CAPTION_GAP = 0.26f

    /** Tracking on the caption. A small line over a huge number needs the air. */
    private const val CAPTION_TRACKING = 0.18f

    /** Borrowed when the chosen face cannot draw what the caption says. */
    private const val CAPTION_FALLBACK = "sans-serif-light"

    /**
     * A typeface for the number.
     *
     * [fontRes] is a bundled font; when it is 0 the style is a system family resolved by name.
     */
    data class Style(
        val label: String,
        val fontRes: Int = 0,
        val family: String = "",
        val weight: Int = Typeface.NORMAL
    )

    val STYLES = listOf(
        Style("Bodoni Moda", fontRes = R.font.bodonimoda_display),
        Style("Cinzel", fontRes = R.font.cinzel_medium),
        Style("Cormorant Garamond", fontRes = R.font.cormorantgaramond_light),
        Style("Playfair Display", fontRes = R.font.playfairdisplay_medium),
        Style("Sans Thin", family = "sans-serif-thin"),
        Style("Sans Light", family = "sans-serif-light"),
        Style("Sans", family = "sans-serif"),
        Style("Sans Medium", family = "sans-serif-medium"),
        Style("Sans Bold", family = "sans-serif", weight = Typeface.BOLD),
        Style("Condensed Light", family = "sans-serif-condensed-light"),
        Style("Condensed", family = "sans-serif-condensed"),
        Style("Condensed Bold", family = "sans-serif-condensed", weight = Typeface.BOLD),
        Style("System Serif", family = "serif"),
        Style("System Serif Bold", family = "serif", weight = Typeface.BOLD),
        Style("Monospace", family = "monospace")
    )

    fun style(index: Int): Style = STYLES[index.coerceIn(STYLES.indices)]

    /**
     * Days from today until [target].
     *
     * A null target keeps the original behaviour: 1 January of next year, recalculated daily, so
     * on 31 Dec this returns 1 and on 1 Jan it returns 365 (or 366). A chosen target is fixed and
     * sticks at 0 once the day arrives, rather than counting into negatives.
     */
    fun daysLeft(target: LocalDate?, today: LocalDate = LocalDate.now()): Int {
        val end = target ?: LocalDate.of(today.year + 1, 1, 1)
        return ChronoUnit.DAYS.between(today, end).toInt().coerceAtLeast(0)
    }

    /**
     * The real screen size, in portrait.
     *
     * Deliberately not WallpaperManager.desiredMinimumWidth/Height: One UI and other launchers
     * ask for a square max-edge canvas (2340x2340 on a 1080x2340 phone) and then show an
     * off-centre, screen-sized slice of it. A bitmap with the screen's own shape has nothing
     * left to crop, so the middle of the bitmap is the middle of the screen.
     */
    fun wallpaperSize(context: Context): Pair<Int, Int> {
        val mode = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.mode
        val a = mode?.physicalWidth ?: context.resources.displayMetrics.widthPixels
        val b = mode?.physicalHeight ?: context.resources.displayMetrics.heightPixels
        return min(a, b) to max(a, b)
    }

    /**
     * Whether [style] can draw every character of [text] itself.
     *
     * The bundled serifs are subset, so they carry Latin and common punctuation and little
     * else. A Typeface built from a single file has no fallback chain behind it, so asking
     * one for a character it does not have puts .notdef boxes on the wallpaper rather than
     * quietly borrowing another face - the caption drops to [CAPTION_FALLBACK] instead.
     * System families bring their own fallback, so they always answer true.
     */
    fun canDraw(context: Context, style: Style, text: String): Boolean {
        if (style.fontRes == 0) return true
        val paint = Paint().apply { typeface = typeface(context, style) }
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val character = String(Character.toChars(codePoint))
            if (character.isNotBlank() && !paint.hasGlyph(character)) return false
            i += Character.charCount(codePoint)
        }
        return true
    }

    fun typeface(context: Context, style: Style): Typeface =
        if (style.fontRes != 0) {
            runCatching { ResourcesCompat.getFont(context, style.fontRes) }
                .getOrNull() ?: Typeface.SERIF
        } else {
            // An unknown family resolves to the system default rather than throwing, so a
            // family missing on some OEM skin degrades instead of crashing the worker.
            Typeface.create(style.family, style.weight)
        }

    /** Chosen canvas, one number centred on it, and the caption above it if there is one. */
    fun render(
        context: Context,
        width: Int,
        height: Int,
        days: Int,
        settings: WallpaperSettings
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        render(context, bitmap, days, settings)
        return bitmap
    }

    /**
     * The same paint, into a bitmap that already exists.
     *
     * The settings screen repaints on every swatch tap, and a 540x1170 ARGB_8888 bitmap is
     * about 2.5 MB; allocating a fresh one per tap churns the heap on the input path for no
     * reason, because the size never changes. SRC rather than the default SrcOver, so a
     * reused buffer starts clean instead of compositing onto the previous paint.
     */
    fun render(context: Context, bitmap: Bitmap, days: Int, settings: WallpaperSettings) {
        val width = bitmap.width
        val height = bitmap.height
        val canvas = Canvas(bitmap)
        canvas.drawColor(settings.backdropColor, PorterDuff.Mode.SRC)

        val text = days.toString()
        val style = style(settings.styleIndex)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = settings.inkColor
            textAlign = Paint.Align.LEFT
            typeface = typeface(context, style)
            // Tabular, lining figures where the font offers them.
            fontFeatureSettings = "'tnum' 1, 'lnum' 1"
        }

        val bounds = Rect()

        // Size to a constant ink HEIGHT first, so 9, 60 and 365 carry the same optical
        // weight instead of the number quietly shrinking as digits get added.
        paint.textSize = height * 0.25f
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.height() > 0) {
            paint.textSize *= (height * TARGET_HEIGHT) / bounds.height()
        }

        // Then pull it back in if a three-digit number runs wide.
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() > width * MAX_WIDTH) {
            paint.textSize *= (width * MAX_WIDTH) / bounds.width()
        }

        paint.textSize = min(paint.textSize, height.toFloat())
        paint.getTextBounds(text, 0, text.length, bounds)

        val caption = settings.caption.trim()
        if (caption.isEmpty()) {
            // Centre on the glyphs' own ink box, both ways. Centring on advance width leaves a
            // tabular 1 (wide side bearings) visibly off to one side.
            val x = width / 2f - (bounds.left + bounds.right) / 2f
            val y = height / 2f - (bounds.top + bounds.bottom) / 2f
            canvas.drawText(text, x, y, paint)
            return
        }

        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = settings.inkColor
            // Advance-width centring, which accounts for tracking; the ink box may not.
            textAlign = Paint.Align.CENTER
            letterSpacing = CAPTION_TRACKING
            typeface = if (canDraw(context, style, caption)) {
                typeface(context, style)
            } else {
                Typeface.create(CAPTION_FALLBACK, Typeface.NORMAL)
            }
            textSize = bounds.height() * CAPTION_RATIO
        }

        val captionWidth = captionPaint.measureText(caption)
        if (captionWidth > width * MAX_WIDTH) {
            captionPaint.textSize *= (width * MAX_WIDTH) / captionWidth
        }

        val captionBounds = Rect()
        captionPaint.getTextBounds(caption, 0, caption.length, captionBounds)

        // Centre caption + gap + number as one block, so the composition doesn't go top-heavy
        // the moment a caption appears.
        val gap = bounds.height() * CAPTION_GAP
        val block = captionBounds.height() + gap + bounds.height()
        val top = (height - block) / 2f

        // Tracking leaves a trailing space after the last glyph, which pulls an advance-centred
        // run half a step to the left. Put it back.
        val captionX = width / 2f + (captionPaint.letterSpacing * captionPaint.textSize) / 2f
        canvas.drawText(caption, captionX, top - captionBounds.top, captionPaint)

        val numberTop = top + captionBounds.height() + gap
        canvas.drawText(
            text,
            width / 2f - (bounds.left + bounds.right) / 2f,
            numberTop - bounds.top,
            paint
        )
    }
}
