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
import com.example.whiteboardapp.manager.AlignmentHelper
import com.example.whiteboardapp.manager.ShapeDrawingHandler
import com.example.whiteboardapp.manager.CanvasTransformManager
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.DrawingTool
import com.example.whiteboardapp.model.TextObject
import com.example.whiteboardapp.utils.PerformanceOptimizer
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel
import kotlin.math.abs
import kotlin.math.min
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave

/**
 * The main custom view for the whiteboard. It handles rendering all objects,
 * user touch input, panning, zooming, and tool interactions.
 */
class WhiteboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var viewModel: WhiteboardViewModel? = null
    private var shapeDrawingHandler: ShapeDrawingHandler = ShapeDrawingHandler(this)
    private val transformManager = CanvasTransformManager()

    // Gesture detectors for handling complex touch events like pinch-to-zoom and double-tap.
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private var velocityTracker: VelocityTracker? = null
    private val flingAnimator = ValueAnimator()

    // State variables
    private var currentTool: DrawingTool = DrawingTool.Pen
    private var allObjects = listOf<DrawingObject>()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isTransforming = false
    private var isScaling = false
    private var isPanning = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    // Track canvas panning state for select mode
    private var isCanvasPanning = false
    private var panStartX = 0f
    private var panStartY = 0f

    // Canvas size multiplier (3x of view size)
    private var canvasMultiplier = 3f
    private val maxBitmapSizeMB = 80f
    private var canvasWidth = 0
    private var canvasHeight = 0

    // Track if this is a simple tap vs drag/pan
    private var isTap = true
    private val tapSlop = 10f // Movement threshold to distinguish tap from drag

    // Boolean for centering the canvas view
    private var isCanvasInitialized = false

    // --- Paint Objects ---
    private val drawPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val canvasPaint = Paint(Paint.DITHER_FLAG)


    private val gridPaint = Paint().apply {
        color = "#E0E0E0".toColorInt()
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

    private val alignmentHelper = AlignmentHelper()
    private var currentAlignmentGuides = listOf<AlignmentHelper.AlignmentGuide>()
    private var alignmentEnabled = true // Can be toggled via settings

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        // Hardware acceleration is crucial for smooth canvas operations.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setAlignmentEnabled(enabled: Boolean) {
        alignmentEnabled = enabled
    }

    fun setViewModel(vm: WhiteboardViewModel) {
        viewModel = vm
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel?.apply {
            // Observe changes in the ViewModel and update the view's state.
            currentTool.observeForever { tool ->
                this@WhiteboardView.currentTool = tool
                isTransforming = false
                isCanvasPanning = false
                isPanning = false
            }
            strokeWidth.observeForever { width -> drawPaint.strokeWidth = width }
            strokeColor.observeForever { color -> drawPaint.color = color }
            drawingObjects.observeForever { objects ->
                allObjects = objects
                refreshCanvas()
            }
        }
    }

    /**
     * Calculates safe canvas dimensions that won't exceed memory limits
     */
    private fun calculateSafeCanvasDimensions(viewWidth: Int, viewHeight: Int): Pair<Int, Int> {
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels

        // Adjust multiplier based on screen size
        canvasMultiplier = when {
            screenWidthPx > 3000 -> 1.2f  // Large IFPs (4K+)
            screenWidthPx > 2000 -> 1.5f  // Tablets/smaller IFPs
            screenWidthPx > 1200 -> 2.0f  // Large phones/small tablets
            else -> 2.5f                   // Regular phones
        }

        // Calculate theoretical canvas size with multiplier
        var testWidth = (viewWidth * canvasMultiplier).toInt()
        var testHeight = (viewHeight * canvasMultiplier).toInt()

        // Calculate memory required (ARGB_8888 = 4 bytes per pixel)
        var requiredMemoryMB = (testWidth * testHeight * 4f) / (1024f * 1024f)

        // If it exceeds limit, scale down further
        if (requiredMemoryMB > maxBitmapSizeMB) {
            val scaleFactor = kotlin.math.sqrt(maxBitmapSizeMB / requiredMemoryMB)
            testWidth = (testWidth * scaleFactor).toInt()
            testHeight = (testHeight * scaleFactor).toInt()

            android.util.Log.w("WhiteboardView",
                "Canvas size reduced to fit memory: ${testWidth}x${testHeight} " +
                        "(~${String.format("%.1f", testWidth * testHeight * 4f / (1024f * 1024f))} MB)")
        }

        // Ensure minimum size (at least equal to view size)
        testWidth = maxOf(testWidth, viewWidth)
        testHeight = maxOf(testHeight, viewHeight)

        return Pair(testWidth, testHeight)
    }

    /**
     * Sets up the off-screen bitmap and canvas when the view's size is determined.
     * The canvas is created larger than the view to allow for panning.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::canvasBitmap.isInitialized) {
            canvasBitmap.recycle()
        }

        // Calculate safe canvas dimensions
        val dimensions = calculateSafeCanvasDimensions(w, h)
        canvasWidth = dimensions.first
        canvasHeight = dimensions.second

        try {
            canvasBitmap = createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap)
            drawCanvas.drawColor(Color.WHITE)

            // Center the canvas initially
            if (!isCanvasInitialized) {
                transformManager.centerCanvas(
                    w.toFloat(), h.toFloat(),
                    canvasWidth.toFloat(), canvasHeight.toFloat()
                )
                // Flip the switch so this code never runs again
                isCanvasInitialized = true
            }
            refreshCanvas()

            android.util.Log.i("WhiteboardView",
                "Canvas created: ${canvasWidth}x${canvasHeight} " +
                        "(~${String.format("%.1f", canvasWidth * canvasHeight * 4f / (1024f * 1024f))} MB)")
        } catch (e: OutOfMemoryError) {
            // Emergency fallback: use 1:1 canvas size
            canvasWidth = w
            canvasHeight = h
            canvasBitmap = createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap)
            drawCanvas.drawColor(Color.WHITE)

            android.widget.Toast.makeText(
                context,
                "Using minimal canvas size due to memory constraints",
                android.widget.Toast.LENGTH_LONG
            ).show()

            android.util.Log.e("WhiteboardView", "OutOfMemoryError: Fallback to 1:1 canvas", e)
        }
    }

    /**
     * Redraws all content onto the off-screen [canvasBitmap].
     * This is an optimization to avoid redrawing every object on every frame.
     */
    private fun refreshCanvas() {
        if (::drawCanvas.isInitialized) {
            // Get visible bounds for optimization
            visibleBounds = transformManager.getVisibleBounds(width.toFloat(), height.toFloat())

            // Clear only the visible area if zoomed in
            if (transformManager.currentScale > 1.5f) {
                // Clear visible region
                drawCanvas.withClip(visibleBounds) {
                    drawColor(Color.WHITE, PorterDuff.Mode.SRC)

                    // Draw grid only in visible area
                    drawGridInBounds(this, visibleBounds)

                    // Draw only visible objects
                    val visibleObjects = PerformanceOptimizer.getVisibleObjects(
                        allObjects,
                        visibleBounds
                    )
                    visibleObjects.forEach { it.draw(this) }
                }
            } else {
                // Full canvas redraw when zoomed out
                drawCanvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
                drawGrid(drawCanvas)
                allObjects.forEach { it.draw(drawCanvas) }
            }

            // Draw selection if present
            viewModel?.let {
                drawCanvas.withSave {
                    it.objectManager.drawSelection(this)
                }
            }

            invalidate() // Request a redraw of the view itself.
        }
    }

    private fun drawGridInBounds(canvas: Canvas, bounds: RectF) {
        val gridSize = 50f
        val startX = (bounds.left / gridSize).toInt() * gridSize
        val endX = ((bounds.right / gridSize).toInt() + 1) * gridSize
        val startY = (bounds.top / gridSize).toInt() * gridSize
        val endY = ((bounds.bottom / gridSize).toInt() + 1) * gridSize

        var x = startX
        while (x <= endX) {
            canvas.drawLine(x, bounds.top, x, bounds.bottom, gridPaint)
            x += gridSize
        }

        var y = startY
        while (y <= endY) {
            canvas.drawLine(bounds.left, y, bounds.right, y, gridPaint)
            y += gridSize
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

    /**
     * The main drawing method. It draws the pre-rendered [canvasBitmap] onto the screen,
     * applying the current zoom and pan transformation.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor("#F5F5F5".toColorInt()) // Background color of the view

        // Apply transformation
        canvas.withSave {
            concat(transformManager.getMatrix())

            // Draw the bitmap canvas
            drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)

            // Draw current path being drawn (for real-time feedback)
            if (currentTool is DrawingTool.Pen && !currentPath.isEmpty) {
                drawPath(currentPath, drawPaint)
            } else if (currentTool is DrawingTool.Shape) {
                shapeDrawingHandler.drawPreview(this)
            }

            // Draw alignment guides when moving objects
            if (currentAlignmentGuides.isNotEmpty()) {
                val visibleBounds =
                    transformManager.getVisibleBounds(width.toFloat(), height.toFloat())
                alignmentHelper.drawGuides(this, currentAlignmentGuides, visibleBounds)
            }
        }

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

    /**
     * Handles all touch events, delegating to gesture detectors and tool-specific handlers.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        // Handle velocity tracking
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        // Always process scale detector for pinch zoom (two fingers)
        scaleGestureDetector.onTouchEvent(event)

        // Process gesture detector only for double tap
        gestureDetector.onTouchEvent(event)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                flingAnimator.cancel()
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                panStartX = event.x
                panStartY = event.y
                isTap = true

                // For select mode, determine if we're selecting an object or panning
                if (currentTool is DrawingTool.Select && event.pointerCount == 1) {
                    val transformed = transformManager.getTransformedPoint(event.x, event.y)
                    val objectManager = viewModel?.objectManager
                    val clickedObject = objectManager?.selectObjectAt(transformed.x, transformed.y)

                    if (clickedObject != null) {
                        // We clicked on an object
                        isTransforming = objectManager.isTransforming()
                        isCanvasPanning = false
                        lastTouchX = transformed.x
                        lastTouchY = transformed.y
                        refreshCanvas()
                    } else {
                        // Clicked on empty space - start canvas panning
                        isCanvasPanning = true
                        objectManager?.clearSelection()
                        refreshCanvas()
                    }
                } else if (!isScaling && event.pointerCount == 1) {
                    // Other tools
                    handleToolAction(event, MotionEvent.ACTION_DOWN)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTap) {
                    val dx = abs(event.x - panStartX)
                    val dy = abs(event.y - panStartY)
                    if (dx > tapSlop || dy > tapSlop) {
                        isTap = false
                    }
                }
                if (currentTool is DrawingTool.Select && event.pointerCount == 1) {
                    if (isCanvasPanning) {
                        // Pan the canvas with one finger in select mode
                        val dx = event.x - panStartX
                        val dy = event.y - panStartY
                        panStartX = event.x
                        panStartY = event.y

                        transformManager.translate(dx, dy)
                        transformManager.constrainTranslation(
                            canvasWidth.toFloat(), canvasHeight.toFloat(),
                            width.toFloat(), height.toFloat()
                        )
                        invalidate()
                    } else {
                        // Handle object manipulation
                        val transformed = transformManager.getTransformedPoint(event.x, event.y)
                        handleSelection(transformed.x, transformed.y, MotionEvent.ACTION_MOVE)
                    }
                } else if (!isScaling && event.pointerCount == 1) {
                    // Other tools
                    handleToolAction(event, MotionEvent.ACTION_MOVE)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentTool is DrawingTool.Select && !isScaling) {
                    if (!isCanvasPanning) {
                        val transformed = transformManager.getTransformedPoint(event.x, event.y)
                        handleSelection(transformed.x, transformed.y, MotionEvent.ACTION_UP)
                    }

                    // Handle fling for canvas panning
                    if (isCanvasPanning) {
                        velocityTracker?.let { tracker ->
                            tracker.computeCurrentVelocity(1000)
                            val velocityX = tracker.xVelocity
                            val velocityY = tracker.yVelocity

                            if (abs(velocityX) > 100 || abs(velocityY) > 100) {
                                handleFling(velocityX, velocityY)
                            }
                        }
                    }
                } else if (!isScaling && event.pointerCount == 1) {
                    handleToolAction(event, MotionEvent.ACTION_UP)
                }

                if (isTap) {
                    performClick()
                }

                activePointerId = MotionEvent.INVALID_POINTER_ID
                isPanning = false
                isCanvasPanning = false
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
                currentAlignmentGuides = emptyList() // Clear guides on new selection
                refreshCanvas()
            }
            MotionEvent.ACTION_MOVE -> {
                val selectedObj = objectManager.getSelectedObject()
                if (isTransforming) {
                    objectManager.updateTransform(x, y)
                } else if (selectedObj != null) {
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    objectManager.moveSelected(dx, dy)

                    if (alignmentEnabled && !isTransforming) {
                        currentAlignmentGuides = alignmentHelper.findAlignmentGuides(
                            selectedObj,
                            allObjects.filter { it.id != selectedObj.id },
                            threshold = 15f / transformManager.currentScale // Adjust for zoom
                        )

                        // If there are guides, snap to them
                        if (currentAlignmentGuides.isNotEmpty()) {
                            val currentPos = PointF(x, y)
                            val snappedPos = alignmentHelper.snapToGuides(
                                currentPos,
                                currentAlignmentGuides,
                                snapDistance = 15f / transformManager.currentScale
                            )

                            // Apply snap adjustment
                            val snapDx = snappedPos.x - currentPos.x
                            val snapDy = snappedPos.y - currentPos.y
                            if (snapDx != 0f || snapDy != 0f) {
                                objectManager.moveSelected(snapDx, snapDy)
                            }
                        }
                    }
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
                currentAlignmentGuides = emptyList()
                refreshCanvas()
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
            isScaling = true
            // Cancel any canvas panning when starting to scale
            isCanvasPanning = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
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
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Allow double-tap zoom in all modes
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