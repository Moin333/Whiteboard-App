package com.example.whiteboardapp.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import com.example.whiteboardapp.manager.ShapeDrawingHandler
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.DrawingTool
import com.example.whiteboardapp.model.TextObject
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel

class WhiteboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var viewModel: WhiteboardViewModel? = null
    private var shapeDrawingHandler: ShapeDrawingHandler = ShapeDrawingHandler(this)

    // --- State ---
    private var currentTool: DrawingTool = DrawingTool.Pen
    private var allObjects = listOf<DrawingObject>()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isTransforming = false

    // --- Paint Objects ---
    private val drawPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val canvasPaint = Paint(Paint.DITHER_FLAG)
    private val selectionPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    // --- Canvas & Path ---
    private lateinit var drawCanvas: Canvas
    private lateinit var canvasBitmap: Bitmap
    private var currentPath = Path()

    fun setViewModel(vm: WhiteboardViewModel) {
        viewModel = vm
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel?.apply {
            currentTool.observeForever { tool -> this@WhiteboardView.currentTool = tool }
            strokeWidth.observeForever { width -> drawPaint.strokeWidth = width }
            strokeColor.observeForever { color -> drawPaint.color = color }
            drawingObjects.observeForever { objects ->
                allObjects = objects
                refreshCanvas()
            }
        }
    }

    private fun refreshCanvas() {
        if (::drawCanvas.isInitialized) {
            drawCanvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
            allObjects.forEach { it.draw(drawCanvas) }
            viewModel?.let { it.objectManager.drawSelection(drawCanvas) }
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::canvasBitmap.isInitialized) canvasBitmap.recycle()
        canvasBitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap)
        refreshCanvas()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)

        // Draw real-time previews
        if (currentTool is DrawingTool.Pen && !currentPath.isEmpty) {
            canvas.drawPath(currentPath, drawPaint)
        } else if (currentTool is DrawingTool.Shape) {
            shapeDrawingHandler.drawPreview(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (currentTool) {
            is DrawingTool.Pen -> handlePenDrawing(event)
            is DrawingTool.Shape -> handleShapeDrawing(event)
            is DrawingTool.Select -> handleSelection(event)
            is DrawingTool.Eraser -> handleErasing(event)
            is DrawingTool.Text -> handleTextTool(event)
            else -> return super.onTouchEvent(event)
        }
        return true
    }

    private fun handlePenDrawing(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val newPathObject = DrawingObject.PathObject(
                    path = Path(currentPath),
                    paint = Paint(drawPaint)
                )
                viewModel?.addObject(newPathObject)
                currentPath.reset()
            }
        }
    }

    private fun handleShapeDrawing(event: MotionEvent) {
        val shapeType = (currentTool as? DrawingTool.Shape)?.type ?: return

        // Create fill paint if fill is enabled
        val fillPaint = if (viewModel?.isFillEnabled?.value == true) {
            Paint().apply {
                color = viewModel?.strokeColor?.value ?: Color.BLACK
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        } else {
            null
        }

        val newShape = shapeDrawingHandler.handleShapeDrawing(event, shapeType, drawPaint, fillPaint)
        if (newShape != null) {
            viewModel?.addObject(newShape)
        }
    }

    private fun handleSelection(event: MotionEvent) {
        val objectManager = viewModel?.objectManager ?: return
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // selectObjectAt will handle both object and handle selection
                objectManager.selectObjectAt(x, y)
                isTransforming = objectManager.isTransforming()
                lastTouchX = x
                lastTouchY = y
                refreshCanvas()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTransforming) {
                    // Update resize/rotate transformation
                    objectManager.updateTransform(x, y)
                } else if (objectManager.getSelectedObject() != null) {
                    // Move the entire object
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    objectManager.moveSelected(dx, dy)
                }
                lastTouchX = x
                lastTouchY = y
                refreshCanvas()
            }
            MotionEvent.ACTION_UP -> {
                if (isTransforming) {
                    objectManager.endTransform()
                    isTransforming = false
                }
                performClick()
            }
        }
    }

    private fun handleErasing(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            viewModel?.eraseObjectsAt(event.x, event.y)
        }
    }

    private fun handleTextTool(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x
            val y = event.y

            // Check if the user tapped on an existing object
            val clickedObject = viewModel?.objectManager?.selectObjectAt(x, y)

            if (clickedObject is TextObject) {
                // Edit existing text object
                TextEditDialog(context, clickedObject) { updatedObject ->
                    viewModel?.updateObject(clickedObject,updatedObject)
                }.show()
            } else {
                // Create a new text object
                TextEditDialog(context, null) { newObject ->
                    // Set the initial position to where the user tapped
                    newObject.x = x
                    newObject.y = y
                    viewModel?.addObject(newObject)
                }.show()
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}