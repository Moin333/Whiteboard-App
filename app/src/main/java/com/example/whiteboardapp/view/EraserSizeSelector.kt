package com.example.whiteboardapp.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * A custom view that displays a set of circles for selecting the eraser size.
 * @property onSizeSelected A callback invoked when a size is chosen.
 */
class EraserSizeSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val sizes = listOf(15f, 25f, 40f, 60f)
    var onSizeSelected: ((Float) -> Unit)? = null
    private var selectedView: View? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER

        sizes.forEach { size ->
            val button = View(context).apply {
                val displaySize = (size * 1.5f).toInt()
                layoutParams = LayoutParams(displaySize.dp, displaySize.dp).apply {
                    setMargins(8.dp, 8.dp, 8.dp, 8.dp)
                }
                background = createCircleDrawable(false)
                tag = size // Store the size in the view's tag
                setOnClickListener {
                    onSizeSelected?.invoke(size)
                    updateSelection(this)
                }
            }
            addView(button)
        }
        // Select the first one by default
        post { getChildAt(1)?.let { updateSelection(it) } }
    }

    private fun updateSelection(view: View) {
        // Deselect the previously selected view
        selectedView?.background = createCircleDrawable(false)

        // Select the new view
        selectedView = view
        selectedView?.background = createCircleDrawable(true)
    }

    private fun createCircleDrawable(isSelected: Boolean): Drawable {
        return ShapeDrawable(OvalShape()).apply {
            paint.color = if (isSelected) Color.DKGRAY else Color.LTGRAY
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}