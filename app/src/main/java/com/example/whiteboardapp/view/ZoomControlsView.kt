package com.example.whiteboardapp.view

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.LinearLayout
import com.example.whiteboardapp.R

class ZoomControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onZoomIn: (() -> Unit)? = null
    var onZoomOut: (() -> Unit)? = null
    var onZoomReset: (() -> Unit)? = null
    var onZoomFit: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        alpha = 0.9f

        val buttonSize = 48.dp
        val margin = 4.dp

        val zoomInButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_zoom_in)
            setBackgroundResource(android.R.drawable.btn_default)
            layoutParams = LayoutParams(buttonSize, buttonSize).apply {
                setMargins(margin, margin, margin, 0)
            }
            setOnClickListener {
                onZoomIn?.invoke()
            }
        }

        val zoomOutButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_zoom_out)
            setBackgroundResource(android.R.drawable.btn_default)
            layoutParams = LayoutParams(buttonSize, buttonSize).apply {
                setMargins(margin, margin, margin, 0)
            }
            setOnClickListener {
                onZoomOut?.invoke()
            }
        }

        val zoomResetButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_zoom_reset)
            setBackgroundResource(android.R.drawable.btn_default)
            layoutParams = LayoutParams(buttonSize, buttonSize).apply {
                setMargins(margin, margin, margin, 0)
            }
            setOnClickListener {
                onZoomReset?.invoke()
            }
        }

        val zoomFitButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_zoom_fit)
            setBackgroundResource(android.R.drawable.btn_default)
            layoutParams = LayoutParams(buttonSize, buttonSize).apply {
                setMargins(margin, margin, margin, margin)
            }
            setOnClickListener { onZoomFit?.invoke() }
        }

        addView(zoomInButton)
        addView(zoomOutButton)
        addView(zoomResetButton)
        addView(zoomFitButton)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}