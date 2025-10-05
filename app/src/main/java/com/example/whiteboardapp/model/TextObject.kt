package com.example.whiteboardapp.model

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withRotation
import java.util.*
import androidx.core.graphics.withSave

/**
 * A specialized [DrawingObject] for rendering and managing text.
 * It uses a [StaticLayout] to handle multi-line text wrapping and alignment.
 */
data class TextObject(
    override val id: String = UUID.randomUUID().toString(),
    var text: String,
    var x: Float,
    var y: Float,
    var textSize: Float = 48f,
    var textColor: Int = Color.BLACK,
    var typeface: Typeface = Typeface.DEFAULT,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var alignment: Paint.Align = Paint.Align.LEFT,
    var maxWidth: Float = 600f,
) : DrawingObject() {

    private val textPaint = TextPaint().apply {
        isAntiAlias = true
    }

    init {
        updatePaint()
    }

    // StaticLayout is used for rendering multi-line text.
    private lateinit var staticLayout: StaticLayout
    // A flag to indicate when the layout needs to be recalculated.
    private var isLayoutDirty = true

    override val bounds: RectF
        get() {
            if (isLayoutDirty) {
                buildStaticLayout()
            }
            val width = staticLayout.width.toFloat()
            val height = staticLayout.height.toFloat()

            // calculate bounds based on alignment
            return when (alignment) {
                Paint.Align.LEFT -> RectF(x, y, x + width, y + height)
                Paint.Align.CENTER -> RectF(x - width / 2, y, x + width / 2, y + height)
                Paint.Align.RIGHT -> RectF(x - width, y, x, y + height)
            }
        }

    /**
     * Updates the internal TextPaint object with the current style properties.
     * This marks the layout as "dirty" to force a recalculation.
     */
    private fun updatePaint() {
        val style = when {
            isBold && isItalic -> Typeface.BOLD_ITALIC
            isBold -> Typeface.BOLD
            isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        textPaint.typeface = Typeface.create(typeface, style)
        textPaint.textSize = this.textSize
        textPaint.color = this.textColor
        isLayoutDirty = true
    }

    // Convenience method to update multiple properties at once.
    fun update(
        newText: String = text,
        newTextSize: Float = textSize,
        newTextColor: Int = textColor,
        newIsBold: Boolean = isBold,
        newIsItalic: Boolean = isItalic,
        newAlignment: Paint.Align = alignment,
    ) {
        this.text = newText
        this.textSize = newTextSize
        this.textColor = newTextColor
        this.isBold = newIsBold
        this.isItalic = newIsItalic
        this.alignment = newAlignment
        updatePaint()
    }

    // Rebuilds the [StaticLayout] which is necessary when text or styling changes.
    private fun buildStaticLayout() {
        val alignmentLayout = when (alignment) {
            Paint.Align.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Paint.Align.CENTER -> Layout.Alignment.ALIGN_CENTER
            Paint.Align.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

        staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, maxWidth.toInt())
            .setAlignment(alignmentLayout)
            .setLineSpacing(0f, 1.2f)
            .setIncludePad(false)
            .build()
        isLayoutDirty = false
    }

    // translate the canvas before drawing
    override fun draw(canvas: Canvas) {
        if (isLayoutDirty) {
            buildStaticLayout()
        }
        val currentBounds = this.bounds
        val centerX = currentBounds.centerX()
        val centerY = currentBounds.centerY()

        // Apply rotation before drawing the text.
        canvas.withRotation(rotation, centerX, centerY) {
            withSave {
                // Translate the canvas to the correct drawing position before drawing the StaticLayout.
                val drawX = when (alignment) {
                    Paint.Align.LEFT, Paint.Align.CENTER, Paint.Align.RIGHT -> currentBounds.left
                }
                translate(drawX, currentBounds.top)
                staticLayout.draw(this)
            }
        }
    }

    override fun contains(x: Float, y: Float): Boolean {
        // Use the same logic as ShapeObject for checking containment in a rotated object.
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        if (rotation != 0f) {
            val rotatedPoint = rotatePoint(x, y, centerX, centerY, -rotation)
            return bounds.contains(rotatedPoint.x, rotatedPoint.y)
        }
        return bounds.contains(x, y)
    }

    override fun move(dx: Float, dy: Float) {
        x += dx
        y += dy
        // The bounds will automatically update on the next access.
    }

    override fun clone(): DrawingObject {
        // Data classes provide a convenient `copy()` method.
        return this.copy()
    }
}