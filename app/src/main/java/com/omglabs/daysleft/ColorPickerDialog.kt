package com.omglabs.daysleft

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

/** Hue/saturation picker plus a hex field, for when none of the quick picks are right. */
object ColorPickerDialog {

    fun show(context: Context, titleRes: Int, initial: Int, onPicked: (Int) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val picker = view.findViewById<ColorPickerView>(R.id.picker)
        val preview = view.findViewById<View>(R.id.swatch_preview)
        val hex = view.findViewById<EditText>(R.id.hex)

        var current = opaque(initial)
        // The picker writes the hex field and the hex field writes the picker, so each side
        // has to know when an edit is its own echo coming back.
        var echo = false

        fun showPreview(color: Int) {
            preview.background = Swatches.solid(context, color)
        }

        picker.color = current
        showPreview(current)
        hex.setText(Palette.toHex(current))

        picker.onColorChanged = { color ->
            current = opaque(color)
            showPreview(current)
            echo = true
            hex.setText(Palette.toHex(current))
            echo = false
        }

        hex.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (echo) return
                val parsed = parseHex(s?.toString().orEmpty()) ?: return
                current = parsed
                showPreview(current)
                picker.setColor(current, notify = false)
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(R.string.use_colour) { _, _ -> onPicked(current) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Only full-length hex counts, so the field doesn't lurch about mid-typing. */
    private fun parseHex(raw: String): Int? {
        val digits = raw.removePrefix("#")
        if (digits.length != 6) return null
        return runCatching { opaque(Color.parseColor("#$digits")) }.getOrNull()
    }

    /** A wallpaper has nothing behind it, so alpha is always 100%. */
    private fun opaque(color: Int) = color or 0xFF000000.toInt()
}
