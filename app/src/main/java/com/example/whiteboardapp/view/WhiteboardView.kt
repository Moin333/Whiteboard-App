package com.example.whiteboardapp.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.createBitmap
import com.example.whiteboardapp.manager.ShapeDrawingHandler
import com.example.whiteboardapp.manager.CanvasTransformManager
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.DrawingTool
import com.example.whiteboardapp.model.TextObject
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel
import kotlin.math.abs
import kotlin.math.min

class WhiteboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var viewModel: WhiteboardViewModel? = null
    private var shapeDrawingHandler: ShapeDrawingHandler = ShapeDrawingHandler(this)
    private val transformManager = CanvasTransformManager()

    // Gesture detectors
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private var velocityTracker: VelocityTracker? = null
    private val flingAnimator = ValueAnimator()

    // --- State ---
    private var currentTool: DrawingTool = DrawingTool.Pen
    private var allObjects = listOf<DrawingObject>()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isTransforming = false
    private var isScaling = false
    private var isPanning = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var hasSelectedObject = false // Track if we have a selected object

    // Canvas size multiplier (3x of view size)
    private val canvasMultiplier = 3f
    private var canvasWidth = 0
    private var canvasHeight = 0

    // --- Paint Objects ---
    private val drawPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val canvasPaint = Paint(Paint.DITHER_FLAG)
    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val zoomTextPaint = Paint().apply {
        textSize = 32f
        color = Color.BLACK
        alpha = 180
        isAntiAlias = true
    }

    // --- Canvas & Path ---
    private lateinit var drawCanvas: Canvas
    private lateinit var canvasBitmap: Bitmap
    private var currentPath = Path()

    // Visible bounds for optimization
    private var visibleBounds = RectF()

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())

        // Enable hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setViewModel(vm: WhiteboardViewModel) {
        viewModel = vm
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel?.apply {
            currentTool.observeForever { tool ->
                this@WhiteboardView.currentTool = tool
                // Reset pan/zoom state when switching tools
                isPanning = false
                isScaling = false
            }
            strokeWidth.observeForever { width -> drawPaint.strokeWidth = width }
            strokeColor.observeForever { color -> drawPaint.color = color }
            drawingObjects.observeForever { objects ->
                allObjects = objects
                refreshCanvas()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (::canvasBitmap.isInitialized) {
            canvasBitmap.recycle()
        }

        // Create canvas 3x the size of the view
        canvasWidth = (w * canvasMultiplier).toInt()
        canvasHeight = (h * canvasMultiplier).toInt()

        canvasBitmap = createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap)
        drawCanvas.drawColor(Color.WHITE)

        // Center the canvas initially
        transformManager.centerCanvas(
            w.toFloat(), h.toFloat(),
            canvasWidth.toFloat(), canvasHeight.toFloat()
        )

        refreshCanvas()
    }

    private fun refreshCanvas() {
        if (::drawCanvas.isInitialized) {
            // Clear canvas
            drawCanvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)

            // Draw grid for visual reference
            drawGrid(drawCanvas)

            // Draw all objects
            allObjects.forEach { it.draw(drawCanvas) }

            // Draw selection if present
            viewModel?.let {
                drawCanvas.save()
                it.objectManager.drawSelection(drawCanvas)
                drawCanvas.restore()
            }

            invalidate()
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 50f
        for (x in 0 until canvasWidth step gridSize.toInt()) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), canvasHeight.toFloat(), gridPaint)
        }
        for (y in 0 until canvasHeight step gridSize.toInt()) {
            canvas.drawLine(0f, y.toFloat(), canvasWidth.toFloat(), y.toFloat(), gridPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Clear the view
        canvas.drawColor(Color.parseColor("#F5F5F5"))

        // Apply transformation
        canvas.save()
        canvas.concat(transformManager.getMatrix())

        // Draw the bitmap canvas
        canvas.drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)

        // Draw current path being drawn (for real-time feedback)
        if (currentTool is DrawingTool.Pen && !currentPath.isEmpty) {
            canvas.drawPath(currentPath, drawPaint)
        } else if (currentTool is DrawingTool.Shape) {
            shapeDrawingHandler.drawPreview(canvas)
        }

        canvas.restore()

        // Draw UI overlays (not transformed)
        drawOverlays(canvas)
    }

    private fun drawOverlays(canvas: Canvas) {
        // Draw zoom percentage
        val zoomPercent = (transformManager.currentScale * 100).toInt()
        val zoomText = "$zoomPercent%"
        canvas.drawText(zoomText, 20f, height - 20f, zoomTextPaint)

        // Draw canvas bounds indicator when zoomed out
        if (transformManager.currentScale < 1f) {
            drawCanvasBounds(canvas)
        }
    }

    private fun drawCanvasBounds(canvas: Canvas) {
        val boundsPaint = Paint().apply {
            color = Color.BLUE
            alpha = 100
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }

        val canvasRect = RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())
        val transformedRect = transformManager.getTransformedRect(canvasRect)
        canvas.drawRect(transformedRect, boundsPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // Check if we're in select mode and have a selected object
        hasSelectedObject = currentTool is DrawingTool.Select &&
                viewModel?.objectManager?.getSelectedObject() != null

        // Handle velocity tracking
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        // Only process gesture detectors if we're not in select mode with a selected object
        if (!hasSelectedObject) {
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                flingAnimator.cancel()
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y

                // Handle tool-specific actions
                if (!isScaling && !isPanning && event.pointerCount == 1) {
                    handleToolAction(event, MotionEvent.ACTION_DOWN)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Allow panning only when not in select mode with a selected object
                if (!hasSelectedObject && (event.pointerCount == 2 || isPanning)) {
                    // Panning is handled by gesture detector
                    return true
                } else if (!isScaling && event.pointerCount == 1) {
                    handleToolAction(event, MotionEvent.ACTION_MOVE)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isScaling && !isPanning && event.pointerCount == 1) {
                    handleToolAction(event, MotionEvent.ACTION_UP)
                }

                // Handle fling only if not in select mode with selected object
                if (!hasSelectedObject) {
                    velocityTracker?.let { tracker ->
                        tracker.computeCurrentVelocity(1000)
                        val velocityX = tracker.xVelocity
                        val velocityY = tracker.yVelocity

                        if (isPanning && (abs(velocityX) > 100 || abs(velocityY) > 100)) {
                            handleFling(velocityX, velocityY)
                        }
                    }
                }

                activePointerId = MotionEvent.INVALID_POINTER_ID
                isPanning = false
                velocityTracker?.recycle()
                velocityTracker = null
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)

                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }

        return true
    }

    private fun handleToolAction(event: MotionEvent, action: Int) {
        // Transform touch coordinates to canvas space
        val transformed = transformManager.getTransformedPoint(event.x, event.y)

        // Check if touch is within canvas bounds
        if (transformed.x < 0 || transformed.x > canvasWidth ||
            transformed.y < 0 || transformed.y > canvasHeight) {
            return
        }

        when (currentTool) {
            is DrawingTool.Pen -> handlePenDrawing(transformed.x, transformed.y, action)
            is DrawingTool.Shape -> handleShapeDrawing(event, transformed.x, transformed.y, action)
            is DrawingTool.Select -> handleSelection(transformed.x, transformed.y, action)
            is DrawingTool.Eraser -> handleErasing(transformed.x, transformed.y, action)
            is DrawingTool.Text -> if (action == MotionEvent.ACTION_DOWN) handleTextTool(transformed.x, transformed.y)
        }
    }

    private fun handlePenDrawing(x: Float, y: Float, action: Int) {
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (!currentPath.isEmpty) {
                    val newPathObject = DrawingObject.PathObject(
                        path = Path(currentPath),
                        paint = Paint(drawPaint)
                    )
                    viewModel?.addObject(newPathObject)
                    currentPath.reset()
                }
            }
        }
    }

    private fun handleShapeDrawing(event: MotionEvent, x: Float, y: Float, action: Int) {
        val shapeType = (currentTool as? DrawingTool.Shape)?.type ?: return

        // Transform the event for the shape handler
        val transformedEvent = MotionEvent.obtain(event)
        transformedEvent.setLocation(x, y)

        val fillPaint = if (viewModel?.isFillEnabled?.value == true) {
            Paint().apply {
                color = viewModel?.strokeColor?.value ?: Color.BLACK
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        } else null

        val newShape = shapeDrawingHandler.handleShapeDrawing(transformedEvent, shapeType, drawPaint, fillPaint)
        if (newShape != null && action == MotionEvent.ACTION_UP) {
            viewModel?.addObject(newShape)
        }

        transformedEvent.recycle()
        invalidate()
    }

    private fun handleSelection(x: Float, y: Float, action: Int) {
        val objectManager = viewModel?.objectManager ?: return

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                objectManager.selectObjectAt(x, y)
                isTransforming = objectManager.isTransforming()
                lastTouchX = x
                lastTouchY = y
                refreshCanvas()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTransforming) {
                    objectManager.updateTransform(x, y)
                } else if (objectManager.getSelectedObject() != null) {
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

    private fun handleErasing(x: Float, y: Float, action: Int) {
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            viewModel?.eraseObjectsAt(x, y)
        }
    }

    private fun handleTextTool(x: Float, y: Float) {
        val clickedObject = viewModel?.objectManager?.selectObjectAt(x, y)

        if (clickedObject is TextObject) {
            TextEditDialog(context, clickedObject) { updatedObject ->
                viewModel?.updateObject(clickedObject.clone(), updatedObject)
            }.show()
        } else {
            TextEditDialog(context, null) { newObject ->
                newObject.x = x
                newObject.y = y
                viewModel?.addObject(newObject)
            }.show()
        }
    }

    private fun handleFling(velocityX: Float, velocityY: Float) {
        flingAnimator.cancel()

        flingAnimator.apply {
            setFloatValues(0f, 1f)
            duration = 1000
            interpolator = DecelerateInterpolator()

            var lastValue = 0f
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                val delta = value - lastValue
                lastValue = value

                val dx = velocityX * delta * 0.002f
                val dy = velocityY * delta * 0.002f

                transformManager.translate(dx, dy)
                transformManager.constrainTranslation(
                    canvasWidth.toFloat(), canvasHeight.toFloat(),
                    width.toFloat(), height.toFloat()
                )
                invalidate()
            }

            start()
        }
    }

    // Zoom control methods
    fun zoomIn() {
        val centerX = width / 2f
        val centerY = height / 2f
        animateZoom(min(transformManager.currentScale * 1.5f, 5f), centerX, centerY)
    }

    fun zoomOut() {
        val centerX = width / 2f
        val centerY = height / 2f
        animateZoom(maxOf(transformManager.currentScale / 1.5f, 0.25f), centerX, centerY)
    }

    fun resetZoom() {
        transformManager.centerCanvas(
            width.toFloat(), height.toFloat(),
            canvasWidth.toFloat(), canvasHeight.toFloat()
        )
        invalidate()
    }

    fun fitToScreen() {
        transformManager.fitToScreen(
            canvasWidth.toFloat(), canvasHeight.toFloat(),
            width.toFloat(), height.toFloat()
        )
        invalidate()
    }

    private fun animateZoom(targetScale: Float, focusX: Float, focusY: Float) {
        ValueAnimator.ofFloat(transformManager.currentScale, targetScale).apply {
            duration = 300
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                transformManager.setScale(scale, focusX, focusY)
                invalidate()
            }
            start()
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Don't allow scaling if we have a selected object
            if (hasSelectedObject) return false

            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (hasSelectedObject) return false

            transformManager.setScale(
                transformManager.currentScale * detector.scaleFactor,
                detector.focusX,
                detector.focusY
            )
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            // Don't allow panning if we have a selected object
            if (hasSelectedObject) return false

            if (!isScaling) {
                // Enable panning with two fingers or when explicitly allowed
                if (e2.pointerCount == 2) {
                    isPanning = true
                    transformManager.translate(-distanceX, -distanceY)
                    transformManager.constrainTranslation(
                        canvasWidth.toFloat(), canvasHeight.toFloat(),
                        width.toFloat(), height.toFloat()
                    )
                    invalidate()
                    return true
                }
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Don't allow double-tap zoom if we have a selected object
            if (hasSelectedObject) return false

            // Zoom to point on double tap
            val targetScale = if (transformManager.currentScale < 2f) 2f else 1f
            animateZoom(targetScale, e.x, e.y)
            return true
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}