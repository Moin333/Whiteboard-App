// In app/src/main/java/com/example/whiteboardapp/view/WhiteboardView.kt

package com.example.whiteboardapp.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap // KTX import added
import com.example.whiteboardapp.model.DrawingPath
import com.example.whiteboardapp.model.DrawingTool
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel

class WhiteboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var viewModel: WhiteboardViewModel? = null
    private var currentTool: DrawingTool = DrawingTool.Pen
    private var isStylus = false

    private val drawPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.BLACK
        strokeWidth = 5f
    }

    // A temporary paint object for the current stroke, allowing for pressure sensitivity
    private var tempPaint: Paint = Paint(drawPaint)

    private val canvasPaint = Paint(Paint.DITHER_FLAG)
    private lateinit var drawCanvas: Canvas
    private lateinit var canvasBitmap: Bitmap

    private var currentPath = Path()
    private var paths = mutableListOf<DrawingPath>()

    fun setViewModel(vm: WhiteboardViewModel) {
        viewModel = vm
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel?.apply {
            // Observe changes in the selected tool
            currentTool.observeForever { tool ->
                this@WhiteboardView.currentTool = tool
                updatePaintForTool(tool)
            }
            // Observe changes in stroke width
            strokeWidth.observeForever { width ->
                drawPaint.strokeWidth = width
            }
            // Observe changes in color
            strokeColor.observeForever { color ->
                if (this@WhiteboardView.currentTool !is DrawingTool.Eraser) {
                    drawPaint.color = color
                }
            }
            // Observe the list of paths to redraw the canvas if needed
            drawingPaths.observeForever { updatedPaths ->
                paths = updatedPaths.toMutableList()
                redrawCanvas()
            }
        }
    }

    private fun updatePaintForTool(tool: DrawingTool) {
        when (tool) {
            is DrawingTool.Pen -> {
                drawPaint.color = viewModel?.strokeColor?.value ?: Color.BLACK
                drawPaint.xfermode = null // Normal drawing mode
            }
            is DrawingTool.Eraser -> {
                drawPaint.color = Color.WHITE
                // PorterDuff.Mode.CLEAR erases the content
                drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            else -> {
                // For other tools
            }
        }
    }

    private fun redrawCanvas() {
        if (::drawCanvas.isInitialized) {
            // Clear the canvas by filling it with white
            drawCanvas.drawColor(Color.WHITE, PorterDuff.Mode.CLEAR)
            drawCanvas.drawColor(Color.WHITE)
            // Redraw all saved paths
            for (dp in paths) {
                drawCanvas.drawPath(dp.path, dp.paint)
            }
            invalidate() // Request a redraw of the view
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::canvasBitmap.isInitialized) canvasBitmap.recycle()

        // **Use KTX extension function for a cleaner call**
        canvasBitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)

        drawCanvas = Canvas(canvasBitmap)
        drawCanvas.drawColor(Color.WHITE)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)
        // Draw the current path being drawn in real-time
        canvas.drawPath(currentPath, tempPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS

        val pressure = if (isStylus) event.pressure.coerceIn(0.1f, 1.0f) else 1.0f

        // Let the specific handler for the current tool manage the event
        return when (currentTool) {
            is DrawingTool.Pen, is DrawingTool.Eraser -> handleDrawing(event, pressure)
            else -> super.onTouchEvent(event)
        }
    }

    private fun handleDrawing(event: MotionEvent, pressure: Float): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Start a new path
                currentPath.reset()
                currentPath.moveTo(x, y)
                // Create a temporary paint object for this specific path
                val adjustedWidth = drawPaint.strokeWidth * pressure
                tempPaint = Paint(drawPaint).apply { strokeWidth = adjustedWidth }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // Continue the path
                currentPath.lineTo(x, y)
                invalidate() // Redraw the view to show the path as it's being drawn
                return true
            }
            MotionEvent.ACTION_UP -> {
                // Finalize the path
                drawCanvas.drawPath(currentPath, tempPaint)
                val drawingPath = DrawingPath(
                    path = Path(currentPath),
                    paint = Paint(tempPaint),
                    strokeWidth = tempPaint.strokeWidth,
                    color = tempPaint.color
                )
                // Add the completed path to the ViewModel
                viewModel?.addPath(drawingPath)
                currentPath.reset()
                invalidate()
                // **Call performClick for accessibility**
                performClick()
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}