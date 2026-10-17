package com.omglabs.til

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

/**
 * The typeface list.
 *
 * Every row previews the live day count in that font, on the backdrop and in the ink already
 * chosen, because a name tells you nothing about what a numeral looks like.
 */
class FontAdapter(
    private val context: Context,
    private val days: Int,
    private val settings: WallpaperSettings
) : BaseAdapter() {

    private val text = days.toString()

    /** Shrink as digits are added, so a three-digit sample still fits its chip. */
    private val sampleSize = when (text.length) {
        1, 2 -> 28f
        3 -> 23f
        else -> 18f
    }

    private val density = context.resources.displayMetrics.density

    override fun getCount() = WallpaperPainter.STYLES.size
    override fun getItem(position: Int) = WallpaperPainter.STYLES[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_font, parent, false)
        val style = WallpaperPainter.STYLES[position]

        view.findViewById<TextView>(R.id.font_sample).apply {
            text = this@FontAdapter.text
            textSize = sampleSize
            typeface = WallpaperPainter.typeface(context, style)
            setTextColor(settings.inkColor)
            background = chip()
        }

        val note = if (style.fontRes != 0) R.string.font_note_bundled else R.string.font_note_system
        view.findViewById<TextView>(R.id.font_name).text = style.label
        view.findViewById<TextView>(R.id.font_note).setText(note)
        val chosen = position == settings.styleIndex
        view.findViewById<TextView>(R.id.font_check).visibility =
            if (chosen) View.VISIBLE else View.INVISIBLE

        // Every view in the row is silent, so the row speaks for itself. isSelected is what
        // turns the tick into something a screen reader reports rather than just draws.
        view.contentDescription = context.getString(
            R.string.a11y_row,
            style.label,
            context.getString(note)
        )
        view.isSelected = chosen

        return view
    }

    private fun chip() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 12 * density
        setColor(settings.backdropColor)
        setStroke((1 * density).toInt(), 0x24FFFFFF)
    }
}
