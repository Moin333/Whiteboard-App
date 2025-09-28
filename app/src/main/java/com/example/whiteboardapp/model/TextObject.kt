package com.example.whiteboardapp.model

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.util.*
import androidx.core.graphics.withSave

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

    private lateinit var staticLayout: StaticLayout
    private var isLayoutDirty = true

    // calculate bounds based on alignment
    override val bounds: RectF
        get() {
            if (isLayoutDirty) {
                buildStaticLayout()
            }
            val width = staticLayout.width.toFloat()
            val height = staticLayout.height.toFloat()

            return when (alignment) {
                Paint.Align.LEFT -> RectF(x, y, x + width, y + height)
                Paint.Align.CENTER -> RectF(x - width / 2, y, x + width / 2, y + height)
                Paint.Align.RIGHT -> RectF(x - width, y, x, y + height)
            }
        }

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
        canvas.withSave {

            // Calculate the top-left corner for drawing, based on the alignment
            val drawX = when (alignment) {
                Paint.Align.LEFT -> x
                Paint.Align.CENTER -> x - staticLayout.width / 2f
                Paint.Align.RIGHT -> x - staticLayout.width
            }

            // Translate to the calculated top-left corner and draw
            translate(drawX, y)
            staticLayout.draw(this)
        }
    }

    override fun contains(x: Float, y: Float): Boolean = bounds.contains(x, y)

    override fun move(dx: Float, dy: Float) {
        x += dx
        y += dy
    }
}