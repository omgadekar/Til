package com.omglabs.til

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    /** Mirror of what is on disk, so a render never has to go back to SharedPreferences. */
    private lateinit var settings: WallpaperSettings

    private lateinit var content: ViewGroup
    private lateinit var preview: ImageView
    private lateinit var summary: TextView
    private lateinit var endDateRow: View
    private lateinit var endDateValue: TextView
    private lateinit var endDateHint: TextView
    private lateinit var styleRow: View
    private lateinit var styleValue: TextView
    private lateinit var styleSample: TextView
    private lateinit var captionRow: View
    private lateinit var captionValue: TextView
    private lateinit var inkSwatches: LinearLayout
    private lateinit var bgSwatches: LinearLayout
    private lateinit var contrastWarning: TextView
    private lateinit var dailyRow: View
    private lateinit var dailySwitch: SwitchCompat
    private lateinit var applyButton: TextView

    /**
     * One bitmap for the life of the screen, repainted in place.
     *
     * The preview is a live mirror of the settings, so it is repainted on every change; at
     * 540x1170 ARGB_8888 that is about 2.5 MB an allocation if it is rebuilt each time.
     */
    private val previewBitmap by lazy {
        Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
    }

    /** Setting a wallpaper is slow enough to double-tap through. */
    private var applying = false

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        content = findViewById(R.id.content)
        preview = findViewById(R.id.preview)
        summary = findViewById(R.id.summary)
        endDateRow = findViewById(R.id.end_date_row)
        endDateValue = findViewById(R.id.end_date_value)
        endDateHint = findViewById(R.id.end_date_hint)
        styleRow = findViewById(R.id.style_row)
        styleValue = findViewById(R.id.style_value)
        styleSample = findViewById(R.id.style_sample)
        captionRow = findViewById(R.id.caption_row)
        captionValue = findViewById(R.id.caption_value)
        inkSwatches = findViewById(R.id.ink_swatches)
        bgSwatches = findViewById(R.id.bg_swatches)
        contrastWarning = findViewById(R.id.contrast_warning)
        dailyRow = findViewById(R.id.daily_row)
        dailySwitch = findViewById(R.id.daily_switch)
        applyButton = findViewById(R.id.apply_now)

        // Rounds the bitmap corners to the bezel; the outline comes from the background.
        preview.clipToOutline = true
        preview.setImageBitmap(previewBitmap)

        settings = Prefs.settings(this)

        buildSwatches(inkSwatches, Palette.INKS) { pickInk(it) }
        buildSwatches(bgSwatches, Palette.BACKDROPS) { pickBackdrop(it) }

        endDateRow.setOnClickListener { pickEndDate() }
        styleRow.setOnClickListener { pickStyle() }
        captionRow.setOnClickListener { editCaption() }

        findViewById<View>(R.id.ink_custom).setOnClickListener {
            ColorPickerDialog.show(this, R.string.pick_number_colour, settings.inkColor) { pickInk(it) }
        }
        findViewById<View>(R.id.bg_custom).setOnClickListener {
            ColorPickerDialog.show(this, R.string.pick_background_colour, settings.backdropColor) {
                pickBackdrop(it)
            }
        }

        applyButton.setOnClickListener { applyNow() }

        // The whole row is the target; the switch itself is just the indicator.
        showDaily(Prefs.isEnabled(this))
        dailyRow.setOnClickListener { setDaily(!dailySwitch.isChecked) }

        refresh()
    }

    /** Left open across midnight, the count on screen would otherwise be yesterday. */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    /**
     * Repaints the small preview and every control that mirrors it.
     *
     * [animate] marks a change the user just made, which is the only time the page is allowed
     * to move; it does not cover the preview, which is never animated. The preview is a live
     * mirror of the controls, so it updates in the same frame as the tap that caused it - a
     * crossfade there would put a fifth of a second between the haptic and the colour the
     * user is trying to judge, and would be a transition between two states rather than the
     * continuous feedback this is meant to be.
     */
    private fun refresh(animate: Boolean = false) {
        val days = WallpaperPainter.daysLeft(settings.targetDate)
        WallpaperPainter.render(this, previewBitmap, days, settings)
        // Same bitmap object throughout, so the view has to be told its pixels moved.
        preview.invalidate()

        val reached = days == 0 && settings.targetDate != null
        val count = if (reached) {
            getString(R.string.target_reached)
        } else {
            resources.getQuantityString(R.plurals.days_left, days, days)
        }
        summary.text = count
        // The preview is the whole point of the screen, so it describes what it is showing
        // rather than announcing itself as a generic image.
        preview.contentDescription = getString(R.string.preview_desc_count, count)

        val target = settings.targetDate
        endDateValue.text = target
            ?.let { getString(R.string.end_date_fixed, dateFormat.format(it)) }
            ?: getString(R.string.new_year_target)
        endDateHint.setText(
            if (target == null) R.string.end_date_hint_rolling else R.string.end_date_hint_fixed
        )
        describe(endDateRow, R.string.section_countdown, endDateValue.text)

        val style = WallpaperPainter.style(settings.styleIndex)
        styleValue.text = style.label
        // The typeface, not the colours: an ink-black sample would vanish on this row.
        styleSample.text = days.toString()
        styleSample.typeface = WallpaperPainter.typeface(this, style)
        describe(styleRow, R.string.section_typeface, style.label)

        val caption = settings.caption
        captionValue.text = caption.ifBlank { getString(R.string.label_none) }
        captionValue.setTextColor(
            ContextCompat.getColor(this, if (caption.isBlank()) R.color.text_muted else R.color.text_primary)
        )
        describe(captionRow, R.string.section_label_text, captionValue.text)

        markSelected(inkSwatches, Palette.INKS, settings.inkColor)
        markSelected(bgSwatches, Palette.BACKDROPS, settings.backdropColor)

        // Both colours chosen freely, so it is easy to land on an unreadable wallpaper.
        // 3.0 is the WCAG AA floor for large text, which is all this wallpaper ever shows.
        val readable = Palette.contrastRatio(settings.inkColor, settings.backdropColor) >= 3.0
        showWarning(show = !readable, animate = animate)
    }

    /**
     * A row is a ViewGroup, so left alone a screen reader reads its children as loose
     * fragments - the value with no idea what it sets. One description on the row replaces
     * them, which is why the section headings above these rows are marked silent.
     */
    private fun describe(row: View, sectionRes: Int, value: CharSequence) {
        row.contentDescription = getString(R.string.a11y_row, getString(sectionRes), value)
    }

    /**
     * The warning takes a row of height with it, so the rest of the page has to move too.
     *
     * areAnimatorsEnabled() is the system answer to "should this move at all": it goes false
     * when the user turns animations off, which Accessibility -> Remove animations does. The
     * warning still appears, it just appears rather than slides.
     */
    private fun showWarning(show: Boolean, animate: Boolean) {
        val target = if (show) View.VISIBLE else View.GONE
        if (contrastWarning.visibility == target) return
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            TransitionManager.beginDelayedTransition(
                content,
                AutoTransition().setDuration(LAYOUT_SHIFT_MS)
            )
        }
        contrastWarning.visibility = target
    }

    private fun pickInk(color: Int) {
        Prefs.setInkColor(this, color)
        settings = settings.copy(inkColor = color)
        refresh(animate = true)
    }

    private fun pickBackdrop(color: Int) {
        Prefs.setBackdropColor(this, color)
        settings = settings.copy(backdropColor = color)
        refresh(animate = true)
    }

    /** Keeps the indicator, the spoken state and the stored flag saying the same thing. */
    private fun showDaily(enabled: Boolean) {
        dailySwitch.isChecked = enabled
        dailyRow.contentDescription = getString(
            R.string.a11y_row,
            getString(R.string.update_daily),
            getString(if (enabled) R.string.a11y_state_on else R.string.a11y_state_off)
        )
    }

    private fun setDaily(enabled: Boolean) {
        dailyRow.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        showDaily(enabled)
        Prefs.setEnabled(this, enabled)
        if (enabled) {
            WallpaperWorker.schedule(this)
            Toast.makeText(this, R.string.daily_on, Toast.LENGTH_SHORT).show()
        } else {
            WorkManager.getInstance(this).cancelUniqueWork(WallpaperWorker.WORK_NAME)
            Toast.makeText(this, R.string.daily_off, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildSwatches(
        row: LinearLayout,
        swatches: List<Palette.Swatch>,
        onPick: (Int) -> Unit
    ) {
        row.removeAllViews()
        // The view is the touch target and the colour is drawn inset inside it, so the chips
        // stay small and close together while every one of them still clears 48dp.
        val size = (Swatches.TARGET_DP * dp).toInt()
        swatches.forEach { swatch ->
            val chip = View(this)
            chip.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (2 * dp).toInt()
            }
            chip.contentDescription = swatch.label
            chip.isFocusable = true
            chip.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onPick(swatch.color)
            }
            row.addView(chip)
        }
    }

    /** A colour from the picker matches no chip, which is the honest thing to show. */
    private fun markSelected(row: LinearLayout, swatches: List<Palette.Swatch>, color: Int) {
        swatches.forEachIndexed { index, swatch ->
            val chip = row.getChildAt(index) ?: return@forEachIndexed
            val chosen = swatch.color == color
            chip.background = Swatches.chip(this, swatch.color, chosen)
            // Draws the ring; this is what has a screen reader say "selected" as well.
            chip.isSelected = chosen
        }
    }

    private fun pickStyle() {
        val days = WallpaperPainter.daysLeft(settings.targetDate)
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_typeface)
            .setAdapter(FontAdapter(this, days, settings)) { dialog, which ->
                Prefs.setStyleIndex(this, which)
                settings = settings.copy(styleIndex = which)
                refresh(animate = true)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickEndDate() {
        val today = LocalDate.now()
        // A target that has already passed would sit outside the picker minDate.
        val shown = (settings.targetDate ?: LocalDate.of(today.year + 1, 1, 1)).coerceAtLeast(today)
        val dialog = DatePickerDialog(
            this,
            R.style.Theme_Til_DatePicker,
            { _, year, month, day ->
                val picked = LocalDate.of(year, month + 1, day)
                Prefs.setTargetDate(this, picked)
                settings = settings.copy(targetDate = picked)
                refresh(animate = true)
            },
            shown.year,
            shown.monthValue - 1,
            shown.dayOfMonth
        )
        // Calendar.getInstance() is local-now, so today stays selectable in every timezone.
        dialog.datePicker.minDate = Calendar.getInstance().timeInMillis
        dialog.setButton(
            DialogInterface.BUTTON_NEUTRAL,
            getString(R.string.use_new_year)
        ) { _, _ ->
            Prefs.setTargetDate(this, null)
            settings = settings.copy(targetDate = null)
            refresh(animate = true)
        }
        dialog.show()
    }

    private fun editCaption() {
        val pad = (22 * dp).toInt()
        val input = EditText(this).apply {
            setText(settings.caption)
            hint = getString(R.string.label_hint)
            // Dimmer than the default, so the placeholder cannot be read as real content.
            setHintTextColor(ContextCompat.getColor(context, R.color.text_muted))
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(Prefs.CAPTION_MAX_LENGTH))
            setSelection(text.length)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }

        // The note is about what has actually been typed rather than about the font in the
        // abstract, so it follows the field: it arrives the moment a character the face cannot
        // draw is added, and leaves again when it goes.
        val style = WallpaperPainter.style(settings.styleIndex)
        val fallbackNote = TextView(this).apply {
            text = getString(R.string.label_fallback_note, style.label)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            setPadding(0, pad / 2, 0, 0)
        }
        container.addView(fallbackNote)

        fun syncFallbackNote() {
            val drawable = WallpaperPainter.canDraw(this, style, input.text.toString())
            fallbackNote.visibility = if (drawable) View.GONE else View.VISIBLE
        }
        syncFallbackNote()
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = syncFallbackNote()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.label_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ -> saveCaption(input.text.toString()) }
            .setNeutralButton(R.string.label_clear) { _, _ -> saveCaption("") }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Read back through Prefs so the trim and length rules are applied in one place. */
    private fun saveCaption(text: String) {
        Prefs.setCaption(this, text)
        settings = settings.copy(caption = Prefs.caption(this))
        refresh(animate = true)
    }

    /**
     * Writing two full-screen bitmaps out to the wallpaper service takes long enough to tap
     * through, so the button says it is working and stops accepting taps while it does.
     */
    private fun applyNow() {
        if (applying) return
        setApplying(true)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { WallpaperWorker.apply(this@MainActivity) }.isSuccess
            }
            setApplying(false)
            Toast.makeText(
                this@MainActivity,
                if (ok) R.string.wallpaper_set else R.string.wallpaper_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setApplying(busy: Boolean) {
        applying = busy
        applyButton.isEnabled = !busy
        applyButton.setText(if (busy) R.string.setting_wallpaper else R.string.set_now)
    }

    private companion object {
        /** Phone proportions, a little over the size the preview is actually drawn at. */
        const val PREVIEW_WIDTH = 540
        const val PREVIEW_HEIGHT = 1170

        /** The page reflowing around the contrast warning appearing or going away. */
        const val LAYOUT_SHIFT_MS = 200L
    }
}
