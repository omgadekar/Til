package com.omglabs.til

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Saturation/value square with a hue bar under it.
 *
 * The square is filled in two passes (white-to-hue horizontally, then transparent-to-black
 * vertically) rather than with a ComposeShader: hardware acceleration won't compose two
 * gradient shaders below API 28, and this app runs from API 26.
 */
class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val hsv = floatArrayOf(0f, 0f, 1f)

    /** Fired for every drag step, so callers get a live preview. */
    var onColorChanged: ((Int) -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66000000
    }
    private val thumbLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private val square = RectF()
    private val hueBar = RectF()

    private val density = resources.displayMetrics.density
    private val barHeight = 32f * density
    private val gap = 16f * density
    private val radius = 6f * density
    private val thumbRadius = 9f * density

    private var dragging = Region.NONE

    private enum class Region { NONE, SQUARE, HUE }

    var color: Int
        get() = Color.HSVToColor(hsv)
        set(value) = setColor(value, notify = false)

    fun setColor(color: Int, notify: Boolean) {
        Color.colorToHSV(color, hsv)
        invalidate()
        if (notify) onColorChanged?.invoke(this.color)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val left = paddingLeft.toFloat()
        val right = w - paddingRight.toFloat()
        val top = paddingTop.toFloat()
        val bottom = h - paddingBottom.toFloat()
        square.set(left, top, right, (bottom - barHeight - gap).coerceAtLeast(top + 1f))
        hueBar.set(left, bottom - barHeight, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        // Saturation: white on the left, the chosen hue at full chroma on the right.
        fill.shader = LinearGradient(
            square.left, square.top, square.right, square.top,
            Color.WHITE, Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(square, radius, radius, fill)

        // Value: clear at the top, black at the bottom.
        fill.shader = LinearGradient(
            square.left, square.top, square.left, square.bottom,
            Color.TRANSPARENT, Color.BLACK,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(square, radius, radius, fill)

        fill.shader = LinearGradient(
            hueBar.left, hueBar.top, hueBar.right, hueBar.top,
            intArrayOf(
                0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
                0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(),
                0xFFFF0000.toInt()
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(hueBar, radius, radius, fill)
        fill.shader = null

        // Two rings, so the thumb stays visible against both white and black.
        val cx = square.left + hsv[1] * square.width()
        val cy = square.top + (1f - hsv[2]) * square.height()
        thumbLight.strokeWidth = 2f * density
        thumbDark.strokeWidth = 4f * density
        canvas.drawCircle(cx, cy, thumbRadius, thumbDark)
        canvas.drawCircle(cx, cy, thumbRadius, thumbLight)

        val hx = hueBar.left + (hsv[0] / 360f) * hueBar.width()
        val marker = RectF(hx - 3f * density, hueBar.top - 2f * density, hx + 3f * density, hueBar.bottom + 2f * density)
        canvas.drawRoundRect(marker, 3f * density, 3f * density, thumbDark)
        canvas.drawRoundRect(marker, 3f * density, 3f * density, thumbLight)
    }

    /**
     * Handling touch directly bypasses the click plumbing, which is what an accessibility
     * service drives the view through. Routing releases back into it puts that back.
     */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = when {
                    event.y >= hueBar.top - gap / 2f -> Region.HUE
                    else -> Region.SQUARE
                }
                // Keep a scrolling ancestor from stealing the drag halfway through.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP -> {
                dragging = Region.NONE
                performClick()
                // The square is a drag surface with no text in it, so the only way a screen
                // reader user learns what they landed on is to be told the value.
                announceForAccessibility(Palette.toHex(color))
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = Region.NONE
                return true
            }
        }
        if (dragging == Region.NONE) return false

        when (dragging) {
            Region.HUE -> {
                hsv[0] = (((event.x - hueBar.left) / hueBar.width()) * 360f).coerceIn(0f, 359.99f)
            }
            Region.SQUARE -> {
                hsv[1] = ((event.x - square.left) / square.width()).coerceIn(0f, 1f)
                hsv[2] = (1f - (event.y - square.top) / square.height()).coerceIn(0f, 1f)
            }
            Region.NONE -> Unit
        }
        invalidate()
        onColorChanged?.invoke(color)
        return true
    }
}
