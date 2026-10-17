package com.omglabs.til

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.content.ContextCompat

/** Circular colour chips, shared by the quick-pick rows and the picker preview. */
object Swatches {

    private const val EDGE = 0x44FFFFFF
    private const val RIPPLE = 0x33FFFFFF

    /** Keeps the chip clear of the ring, and the ring clear of the edge of the view. */
    private const val CHIP_INSET_DP = 5

    /** The chip view itself, so every quick pick clears the 48dp touch-target floor. */
    const val TARGET_DP = 48

    /** A plain filled circle: the preview in the colour picker, where nothing is selectable. */
    fun solid(context: Context, color: Int): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(px(context, 1), EDGE)
    }

    /**
     * A quick-pick chip.
     *
     * The ring is always there and only its colour changes, so selecting a swatch does not
     * resize the colour underneath it - the eye is being asked to compare these against each
     * other, and a chip that grows when picked makes that comparison a moving target. The
     * ring stays detached rather than thickening the border, because a white border on a
     * white swatch is invisible.
     */
    fun chip(context: Context, color: Int, selected: Boolean): Drawable {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(
                px(context, 2),
                if (selected) ContextCompat.getColor(context, R.color.accent) else Color.TRANSPARENT
            )
        }
        val swatch = InsetDrawable(solid(context, color), px(context, CHIP_INSET_DP))
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(
            ColorStateList.valueOf(RIPPLE),
            LayerDrawable(arrayOf(ring, swatch)),
            mask
        )
    }

    private fun px(context: Context, dp: Int) =
        (dp * context.resources.displayMetrics.density).toInt()
}
