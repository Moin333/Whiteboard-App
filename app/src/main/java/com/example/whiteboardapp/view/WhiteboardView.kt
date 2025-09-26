package com.example.whiteboardapp.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
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

    // --- Paint Objects ---
    private val drawPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.BLACK
        strokeWidth = 5f
    }

    private var tempPaint: Paint = Paint(drawPaint)
    private val canvasPaint = Paint(Paint.DITHER_FLAG)
    private val eraserCursorPaint = Paint().apply {
        isAntiAlias = true
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    // --- Canvas & Path ---
    private lateinit var drawCanvas: Canvas
    private lateinit var canvasBitmap: Bitmap
    private var currentPath = Path()
    private var allPaths = mutableListOf<DrawingPath>() // Local copy for consistent rendering

    // --- Eraser State ---
    private var eraserRadius = 20f
    private var eraserX = 0f
    private var eraserY = 0f
    private var showEraserCursor = false
    private var isErasing = false

    fun setViewModel(vm: WhiteboardViewModel) {
        viewModel = vm
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel?.apply {
            currentTool.observeForever { tool ->
                this@WhiteboardView.currentTool = tool
                // Reset eraser cursor when switching tools
                if (tool !is DrawingTool.Eraser) {
                    showEraserCursor = false
                    isErasing = false
                } else {
                    // When switching to eraser, ensure canvas shows current state
                    refreshCanvas()
                }
                invalidate()
            }

            strokeWidth.observeForever { width ->
                drawPaint.strokeWidth = width
            }

            strokeColor.observeForever { color ->
                drawPaint.color = color
            }

            eraserRadius.observeForever { radius ->
                this@WhiteboardView.eraserRadius = radius
            }

            drawingPaths.observeForever { paths ->
                // Update local copy and refresh canvas
                allPaths.clear()
                allPaths.addAll(paths)
                refreshCanvas()
            }
        }
    }

    private fun refreshCanvas() {
        if (::drawCanvas.isInitialized) {
            // Clear the canvas completely
            drawCanvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)

            // Redraw all paths
            for (path in allPaths) {
                drawCanvas.drawPath(path.path, path.paint)
            }

            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::canvasBitmap.isInitialized) canvasBitmap.recycle()

        canvasBitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap)
        drawCanvas.drawColor(Color.WHITE)

        // Redraw existing paths if any
        refreshCanvas()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Always draw the bitmap first
        canvas.drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)

        // Draw the current path being drawn (only for pen tool)
        if (currentTool is DrawingTool.Pen && !currentPath.isEmpty) {
            canvas.drawPath(currentPath, tempPaint)
        }

        // Draw eraser cursor if active
        if (showEraserCursor && currentTool is DrawingTool.Eraser) {
            canvas.drawCircle(eraserX, eraserY, eraserRadius, eraserCursorPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (currentTool) {
            is DrawingTool.Pen -> handleDrawing(event)
            is DrawingTool.Eraser -> handleErasing(event)
            else -> super.onTouchEvent(event)
        }
    }

    private fun handleDrawing(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val pressure = if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            event.pressure.coerceIn(0.1f, 1.0f)
        } else {
            1.0f
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(x, y)
                // Create temp paint for this stroke with pressure sensitivity
                tempPaint = Paint(drawPaint).apply {
                    strokeWidth = this@WhiteboardView.drawPaint.strokeWidth * pressure
                    // Ensure normal blending mode for drawing
                    xfermode = null
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Draw the path to canvas
                drawCanvas.drawPath(currentPath, tempPaint)

                // Create drawing path object and add to ViewModel
                val newPath = DrawingPath(
                    path = Path(currentPath),
                    paint = Paint(tempPaint),
                    strokeWidth = tempPaint.strokeWidth,
                    color = tempPaint.color
                )
                viewModel?.addPath(newPath)

                // Reset current path
                currentPath.reset()
                invalidate()
                performClick()
                return true
            }
        }
        return false
    }

    private fun handleErasing(event: MotionEvent): Boolean {
        eraserX = event.x
        eraserY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                showEraserCursor = true
                isErasing = true
                // Perform erase operation
                viewModel?.eraseAt(eraserX, eraserY, eraserRadius)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // Continue erasing as user moves finger/stylus
                viewModel?.eraseAt(eraserX, eraserY, eraserRadius)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                showEraserCursor = false
                isErasing = false
                performClick()
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    // Method to clear the entire canvas
    fun clearCanvas() {
        if (::drawCanvas.isInitialized) {
            drawCanvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
            allPaths.clear()
            currentPath.reset()
            invalidate()
        }
    }
}