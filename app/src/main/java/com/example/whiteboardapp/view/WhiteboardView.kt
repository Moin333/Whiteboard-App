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
import androidx.lifecycle.LifecycleOwner
import com.example.whiteboardapp.manager.AlignmentHelper
import com.example.whiteboardapp.manager.ShapeDrawingHandler
import com.example.whiteboardapp.manager.CanvasTransformManager
import com.example.whiteboardapp.manager.StylusInputProcessor
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.DrawingTool
import com.example.whiteboardapp.model.StrokePoint
import com.example.whiteboardapp.model.StylusStrokeObject
import com.example.whiteboardapp.model.TextObject
import com.example.whiteboardapp.renderer.VariableWidthStrokeRenderer
import com.example.whiteboardapp.utils.PerformanceOptimizer
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel
import kotlin.math.abs
import kotlin.math.min
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave

/**
 * The main custom view for the whiteboard.
 *
 * Stylus handling overview:
 *  - All [MotionEvent]s are first passed through [stylusInputProcessor] before any other logic.
 *  - If [StylusInputProcessor.ProcessedInput.isPalmRejected] is true the event is consumed
 *    silently — no drawing, no panning, no selection.
 *  - If the source is [StylusInputProcessor.InputSource.ERASER] the view auto-routes to
 *    eraser-mode logic regardless of the toolbar selection.
 *  - For [DrawingTool.Pen] + stylus: historical points are extracted and stored in
 *    [currentStylusPoints] (canvas-space). On ACTION_UP a [StylusStrokeObject] is created
 *    with all the pressure/tilt data. Live preview is drawn via [VariableWidthStrokeRenderer.drawLivePreview].
 *  - For [DrawingTool.Pen] + finger: the original [Path] based approach is retained so that
 *    the app works normally on non-stylus devices.
 *  - [onHoverEvent] is overridden so a cursor dot appears before the stylus touches (essential
 *    on IFPs where the hover range can be 10–20 mm).
 */
class WhiteboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var viewModel: WhiteboardViewModel? = null
    private var shapeDrawingHandler: ShapeDrawingHandler = ShapeDrawingHandler(this)
    private val transformManager = CanvasTransformManager()

    // ── Stylus / Input ────────────────────────────────────────────────────────
    private val stylusInputProcessor = StylusInputProcessor()

    /**
     * Accumulates [StrokePoint]s (canvas-space) while drawing with a stylus.
     * Cleared on ACTION_DOWN and converted to a [StylusStrokeObject] on ACTION_UP.
     */
    private val currentStylusPoints = mutableListOf<StrokePoint>()
    private var isDrawingWithStylus = false

    /**
     * Hover position (screen-space). Set via [onHoverEvent], cleared on touch.
     * Non-null value causes a cursor indicator to be drawn in [onDraw].
     */
    private var hoverScreenPoint: PointF? = null

    // ── Gesture detectors ────────────────────────────────────────────────────
    private val scaleGestureDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector
    private var velocityTracker: VelocityTracker? = null
    private val flingAnimator = ValueAnimator()

    // ── Tool state ────────────────────────────────────────────────────────────
    private var currentTool: DrawingTool = DrawingTool.Pen
    private var allObjects = listOf<DrawingObject>()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isTransforming = false
    private var isScaling = false
    private var isPanning = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var isCanvasPanning = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var isTap = true
    private val tapSlop = 10f

    // ── Canvas sizing ─────────────────────────────────────────────────────────
    private var canvasMultiplier = 3f
    private val maxBitmapSizeMB = 80f
    private var canvasWidth = 0
    private var canvasHeight = 0
    private var isCanvasInitialized = false

    // ── Paint objects ─────────────────────────────────────────────────────────
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

    // ── Off-screen bitmap canvas ──────────────────────────────────────────────
    private lateinit var drawCanvas: Canvas
    private lateinit var canvasBitmap: Bitmap

    /** The finger-mode path being drawn (used when input source is FINGER/UNKNOWN). */
    private var currentPath = Path()

    private var visibleBounds = RectF()

    // ── Alignment ─────────────────────────────────────────────────────────────
    private val alignmentHelper = AlignmentHelper()
    private var currentAlignmentGuides = listOf<AlignmentHelper.AlignmentGuide>()
    private var alignmentEnabled = true

    // ─────────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────────

    init {
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setAlignmentEnabled(enabled: Boolean) { alignmentEnabled = enabled }

    fun setViewModel(vm: WhiteboardViewModel, lifecycleOwner: LifecycleOwner) {
        viewModel = vm
        observeViewModel(lifecycleOwner)
    }

    private fun observeViewModel(lifecycleOwner: LifecycleOwner) {
        viewModel?.apply {
            currentTool.observe(lifecycleOwner) { tool ->
                this@WhiteboardView.currentTool = tool
                isTransforming = false
                isCanvasPanning = false
                isPanning = false
                // Cancel any in-progress drawing when switching tools.
                // This provides immediate visual feedback (preview disappears when
                // tool button is tapped) and prevents reconnection edge cases.
                currentPath.reset()
                currentStylusPoints.clear()
                isDrawingWithStylus = false
            }
            strokeWidth.observe(lifecycleOwner) { width -> drawPaint.strokeWidth = width }
            strokeColor.observe(lifecycleOwner) { color -> drawPaint.color = color }
            drawingObjects.observe(lifecycleOwner) { objects ->
                allObjects = objects
                refreshCanvas()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layout / Size
    // ─────────────────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::canvasBitmap.isInitialized) canvasBitmap.recycle()

        val (cw, ch) = calculateSafeCanvasDimensions(w, h)
        canvasWidth = cw
        canvasHeight = ch

        try {
            canvasBitmap = createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap)
            drawCanvas.drawColor(Color.WHITE)

            if (!isCanvasInitialized) {
                transformManager.centerCanvas(w.toFloat(), h.toFloat(), canvasWidth.toFloat(), canvasHeight.toFloat())
                isCanvasInitialized = true
            }
            refreshCanvas()
        } catch (e: OutOfMemoryError) {
            canvasWidth = w; canvasHeight = h
            canvasBitmap = createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap)
            drawCanvas.drawColor(Color.WHITE)
        }
    }

    private fun calculateSafeCanvasDimensions(viewWidth: Int, viewHeight: Int): Pair<Int, Int> {
        val screenWidthPx = resources.displayMetrics.widthPixels
        canvasMultiplier = when {
            screenWidthPx > 3000 -> 1.2f
            screenWidthPx > 2000 -> 1.5f
            screenWidthPx > 1200 -> 2.0f
            else -> 2.5f
        }
        var tw = (viewWidth * canvasMultiplier).toInt()
        var th = (viewHeight * canvasMultiplier).toInt()
        val requiredMB = (tw * th * 4f) / (1024f * 1024f)
        if (requiredMB > maxBitmapSizeMB) {
            val scale = kotlin.math.sqrt(maxBitmapSizeMB / requiredMB)
            tw = (tw * scale).toInt()
            th = (th * scale).toInt()
        }
        return Pair(maxOf(tw, viewWidth), maxOf(th, viewHeight))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Canvas refresh / draw
    // ─────────────────────────────────────────────────────────────────────────

    private fun refreshCanvas() {
        if (!::drawCanvas.isInitialized) return
        visibleBounds = transformManager.getVisibleBounds(width.toFloat(), height.toFloat())

        if (transformManager.currentScale > 1.5f) {
            drawCanvas.withClip(visibleBounds) {
                drawColor(Color.WHITE, PorterDuff.Mode.SRC)
                drawGridInBounds(this, visibleBounds)
                PerformanceOptimizer.getVisibleObjects(allObjects, visibleBounds).forEach { it.draw(this) }
            }
        } else {
            drawCanvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
            drawGrid(drawCanvas)
            allObjects.forEach { it.draw(drawCanvas) }
        }

        viewModel?.objectManager?.let { om ->
            drawCanvas.withSave { om.drawSelection(this) }
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor("#F5F5F5".toColorInt())

        canvas.withSave {
            concat(transformManager.getMatrix())

            // Committed objects (bitmap)
            drawBitmap(canvasBitmap, 0f, 0f, canvasPaint)

            // ── Live drawing previews ─────────────────────────────────────
            when {
                // Stylus pen: variable-width live preview
                isDrawingWithStylus && currentStylusPoints.isNotEmpty() -> {
                    VariableWidthStrokeRenderer.drawLivePreview(
                        canvas = this,
                        points = currentStylusPoints,
                        color = drawPaint.color,
                        baseWidth = drawPaint.strokeWidth
                    )
                }
                // Finger/mouse pen: classic path preview
                currentTool is DrawingTool.Pen && !currentPath.isEmpty -> {
                    drawPath(currentPath, drawPaint)
                }
                // Shape tool preview
                currentTool is DrawingTool.Shape -> {
                    shapeDrawingHandler.drawPreview(this)
                }
            }

            // Alignment guides
            if (currentAlignmentGuides.isNotEmpty()) {
                alignmentHelper.drawGuides(
                    this,
                    currentAlignmentGuides,
                    transformManager.getVisibleBounds(width.toFloat(), height.toFloat())
                )
            }

            // ── Hover cursor (drawn in canvas space) ──────────────────────
            hoverScreenPoint?.let { screenPt ->
                val canvasPt = transformManager.getTransformedPoint(screenPt.x, screenPt.y)
                // Only draw if within canvas bounds
                if (canvasPt.x in 0f..canvasWidth.toFloat() && canvasPt.y in 0f..canvasHeight.toFloat()) {
                    VariableWidthStrokeRenderer.drawHoverIndicator(
                        canvas = this,
                        x = canvasPt.x,
                        y = canvasPt.y,
                        color = drawPaint.color,
                        baseWidth = drawPaint.strokeWidth
                    )
                }
            }
        }

        drawOverlays(canvas)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Touch handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Main touch event handler.
     *
     * Processing order:
     *  1. Collect velocity for fling.
     *  2. Feed to scale gesture detector (always, even for stylus — pinch zoom must work).
     *  3. Feed to gesture detector (for double-tap zoom).
     *  4. Pass through [stylusInputProcessor] to detect tool type and palm rejection.
     *  5. If eraser tip → auto-erase regardless of toolbar selection.
     *  6. If palm-rejected finger + drawing tool → consume silently, return true.
     *  7. Delegate to per-action handling.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(event)

        // Scale + gesture detectors always receive events (pinch zoom works alongside stylus)
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        // Stylus classification + palm rejection
        val processed = stylusInputProcessor.processEvent(event)

        // Clear hover when the pen touches
        hoverScreenPoint = null

        // ── Eraser tip: auto-erase ────────────────────────────────────────
        if (processed.source == StylusInputProcessor.InputSource.ERASER) {
            handleEraserTipEvent(action, processed.points)
            return true
        }

        // ── Palm rejection for drawing tools ─────────────────────────────
        // Allow finger panning in Select mode (user may rest palm while selecting)
        if (processed.isPalmRejected && currentTool !is DrawingTool.Select) {
            return true
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                flingAnimator.cancel()
                activePointerId = event.getPointerId(0)
                lastTouchX = event.x
                lastTouchY = event.y
                panStartX = event.x
                panStartY = event.y
                isTap = true

                if (currentTool is DrawingTool.Select && event.pointerCount == 1) {
                    val transformed = transformManager.getTransformedPoint(event.x, event.y)
                    val objectManager = viewModel?.objectManager
                    val clickedObject = objectManager?.selectObjectAt(transformed.x, transformed.y)
                    if (clickedObject != null) {
                        isTransforming = objectManager.isTransforming()
                        isCanvasPanning = false
                        lastTouchX = transformed.x
                        lastTouchY = transformed.y
                        refreshCanvas()
                    } else {
                        isCanvasPanning = true
                        objectManager?.clearSelection()
                        refreshCanvas()
                    }
                } else if (!isScaling && event.pointerCount == 1) {
                    handleToolActionDown(event, processed)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTap) {
                    val dx = abs(event.x - panStartX)
                    val dy = abs(event.y - panStartY)
                    if (dx > tapSlop || dy > tapSlop) isTap = false
                }

                if (currentTool is DrawingTool.Select && event.pointerCount == 1) {
                    if (isCanvasPanning) {
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
                        val transformed = transformManager.getTransformedPoint(event.x, event.y)
                        handleSelection(transformed.x, transformed.y, MotionEvent.ACTION_MOVE)
                    }
                } else if (!isScaling && event.pointerCount == 1) {
                    handleToolActionMove(event, processed)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentTool is DrawingTool.Select && !isScaling) {
                    if (!isCanvasPanning) {
                        val transformed = transformManager.getTransformedPoint(event.x, event.y)
                        handleSelection(transformed.x, transformed.y, MotionEvent.ACTION_UP)
                    }
                    if (isCanvasPanning) {
                        velocityTracker?.let { tracker ->
                            tracker.computeCurrentVelocity(1000)
                            val vx = tracker.xVelocity
                            val vy = tracker.yVelocity
                            if (abs(vx) > 100 || abs(vy) > 100) handleFling(vx, vy)
                        }
                    }
                } else if (!isScaling) {
                    handleToolActionUp(event, processed)
                }

                if (isTap) performClick()

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
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newIndex)
                    lastTouchY = event.getY(newIndex)
                    activePointerId = event.getPointerId(newIndex)
                }
            }
        }

        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hover events (stylus proximity before touch)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called when the stylus enters/moves within hover range (typically 5–20 mm above surface).
     * We capture the screen-space hover position and draw a cursor indicator in [onDraw].
     */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_ENTER -> {
                // Only show hover cursor for pen/shape/text tools; suppress in select/eraser
                if (currentTool is DrawingTool.Pen || currentTool is DrawingTool.Shape || currentTool is DrawingTool.Text) {
                    hoverScreenPoint = PointF(event.x, event.y)
                } else {
                    hoverScreenPoint = null
                }
                invalidate()
                true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverScreenPoint = null
                invalidate()
                true
            }
            else -> super.onHoverEvent(event)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Per-action dispatch helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleToolActionDown(
        event: MotionEvent,
        processed: StylusInputProcessor.ProcessedInput
    ) {
        val transformed = transformManager.getTransformedPoint(event.x, event.y)
        if (isOutOfBounds(transformed.x, transformed.y)) return

        when (currentTool) {
            is DrawingTool.Pen -> {
                val isStylus = processed.source == StylusInputProcessor.InputSource.STYLUS
                isDrawingWithStylus = isStylus
                currentPath.reset()
                currentStylusPoints.clear()

                if (isStylus) {
                    // Add the DOWN point(s) (usually one historical-less event on DOWN)
                    addTransformedStylusPoints(processed.points)
                    if (currentStylusPoints.isEmpty()) {
                        currentStylusPoints.add(StrokePoint.plain(transformed.x, transformed.y, event.eventTime))
                    }
                } else {
                    currentPath.moveTo(transformed.x, transformed.y)
                }
            }
            is DrawingTool.Shape -> handleShapeDrawing(event, transformed.x, transformed.y, MotionEvent.ACTION_DOWN)
            is DrawingTool.Select -> handleSelection(transformed.x, transformed.y, MotionEvent.ACTION_DOWN)
            is DrawingTool.Eraser -> handleErasing(transformed.x, transformed.y, MotionEvent.ACTION_DOWN)
            is DrawingTool.Text -> handleTextTool(transformed.x, transformed.y)
        }
    }

    private fun handleToolActionMove(
        event: MotionEvent,
        processed: StylusInputProcessor.ProcessedInput
    ) {
        when (currentTool) {
            is DrawingTool.Pen -> {
                if (isDrawingWithStylus) {
                    // Add ALL extracted points (includes historical samples)
                    addTransformedStylusPoints(processed.points)
                    invalidate() // live preview redrawn in onDraw
                } else {
                    // Finger / mouse: classic path
                    val transformed = transformManager.getTransformedPoint(event.x, event.y)
                    if (!isOutOfBounds(transformed.x, transformed.y)) {
                        currentPath.lineTo(transformed.x, transformed.y)
                        invalidate()
                    }
                }
            }
            is DrawingTool.Shape -> {
                val transformed = transformManager.getTransformedPoint(event.x, event.y)
                handleShapeDrawing(event, transformed.x, transformed.y, MotionEvent.ACTION_MOVE)
            }
            is DrawingTool.Select -> {
                val transformed = transformManager.getTransformedPoint(event.x, event.y)
                handleSelection(transformed.x, transformed.y, MotionEvent.ACTION_MOVE)
            }
            is DrawingTool.Eraser -> {
                val transformed = transformManager.getTransformedPoint(event.x, event.y)
                handleErasing(transformed.x, transformed.y, MotionEvent.ACTION_MOVE)
            }
            else -> {}
        }
    }

    private fun handleToolActionUp(
        event: MotionEvent,
        processed: StylusInputProcessor.ProcessedInput
    ) {
        val transformed = transformManager.getTransformedPoint(event.x, event.y)

        when (currentTool) {
            is DrawingTool.Pen -> {
                if (isDrawingWithStylus) {
                    // Add the final point from the UP event
                    addTransformedStylusPoints(processed.points)

                    if (currentStylusPoints.isNotEmpty()) {
                        val strokeObj = StylusStrokeObject(
                            rawPoints = currentStylusPoints.toList(),
                            baseWidth = drawPaint.strokeWidth,
                            color = drawPaint.color,
                            isTiltEnabled = true
                        )
                        viewModel?.addObject(strokeObj)
                    }
                    currentStylusPoints.clear()
                    isDrawingWithStylus = false
                } else {
                    // Finger/mouse path
                    if (!currentPath.isEmpty) {
                        viewModel?.addObject(
                            DrawingObject.PathObject(
                                path = Path(currentPath),
                                paint = Paint(drawPaint)
                            )
                        )
                        currentPath.reset()
                    }
                }
            }
            is DrawingTool.Shape -> {
                if (!isOutOfBounds(transformed.x, transformed.y)) {
                    handleShapeDrawing(event, transformed.x, transformed.y, MotionEvent.ACTION_UP)
                }
            }
            is DrawingTool.Select -> handleSelection(transformed.x, transformed.y, MotionEvent.ACTION_UP)
            else -> {}
        }
    }

    /**
     * Transforms each screen-space [StrokePoint] in [screenPoints] to canvas space
     * and appends it to [currentStylusPoints].
     * Points outside the canvas bounds are discarded.
     */
    private fun addTransformedStylusPoints(screenPoints: List<StrokePoint>) {
        for (sp in screenPoints) {
            val canvasPt = transformManager.getTransformedPoint(sp.x, sp.y)
            if (!isOutOfBounds(canvasPt.x, canvasPt.y)) {
                currentStylusPoints.add(sp.copy(x = canvasPt.x, y = canvasPt.y))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eraser tip (back of stylus)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles TOOL_TYPE_ERASER events. The eraser tip should erase objects at the touch
     * point regardless of the currently selected toolbar tool.
     */
    private fun handleEraserTipEvent(
        action: Int,
        screenPoints: List<StrokePoint>
    ) {
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE) return
        // Use the last point in the batch (most current position)
        val screenPt = screenPoints.lastOrNull() ?: return
        val canvasPt = transformManager.getTransformedPoint(screenPt.x, screenPt.y)
        if (!isOutOfBounds(canvasPt.x, canvasPt.y)) {
            viewModel?.eraseObjectsAt(canvasPt.x, canvasPt.y)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Existing tool handlers (unchanged logic, extracted for clarity)
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleShapeDrawing(event: MotionEvent, x: Float, y: Float, action: Int) {
        val shapeType = (currentTool as? DrawingTool.Shape)?.type ?: return
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
                currentAlignmentGuides = emptyList()
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
                            threshold = 15f / transformManager.currentScale
                        )
                        if (currentAlignmentGuides.isNotEmpty()) {
                            val snapped = alignmentHelper.snapToGuides(
                                PointF(x, y), currentAlignmentGuides,
                                snapDistance = 15f / transformManager.currentScale
                            )
                            val snapDx = snapped.x - x
                            val snapDy = snapped.y - y
                            if (snapDx != 0f || snapDy != 0f) objectManager.moveSelected(snapDx, snapDy)
                        }
                    }
                }
                lastTouchX = x
                lastTouchY = y
                refreshCanvas()
            }
            MotionEvent.ACTION_UP -> {
                if (isTransforming) { objectManager.endTransform(); isTransforming = false }
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
            TextEditDialog(context, clickedObject) { updated ->
                viewModel?.updateObject(clickedObject.clone(), updated)
            }.show()
        } else {
            TextEditDialog(context, null) { newObject ->
                newObject.x = x; newObject.y = y
                viewModel?.addObject(newObject)
            }.show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Zoom controls
    // ─────────────────────────────────────────────────────────────────────────

    fun zoomIn() { animateZoom(min(transformManager.currentScale * 1.5f, 5f), width / 2f, height / 2f) }
    fun zoomOut() { animateZoom(maxOf(transformManager.currentScale / 1.5f, 0.25f), width / 2f, height / 2f) }

    fun resetZoom() {
        transformManager.centerCanvas(width.toFloat(), height.toFloat(), canvasWidth.toFloat(), canvasHeight.toFloat())
        invalidate()
    }

    fun fitToScreen() {
        transformManager.fitToScreen(canvasWidth.toFloat(), canvasHeight.toFloat(), width.toFloat(), height.toFloat())
        invalidate()
    }

    private fun animateZoom(targetScale: Float, focusX: Float, focusY: Float) {
        ValueAnimator.ofFloat(transformManager.currentScale, targetScale).apply {
            duration = 300
            addUpdateListener {
                transformManager.setScale(it.animatedValue as Float, focusX, focusY)
                invalidate()
            }
            start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drawing helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 50f
        for (x in 0 until canvasWidth step gridSize.toInt())
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), canvasHeight.toFloat(), gridPaint)
        for (y in 0 until canvasHeight step gridSize.toInt())
            canvas.drawLine(0f, y.toFloat(), canvasWidth.toFloat(), y.toFloat(), gridPaint)
    }

    private fun drawGridInBounds(canvas: Canvas, bounds: RectF) {
        val gridSize = 50f
        val startX = (bounds.left / gridSize).toInt() * gridSize
        val endX = ((bounds.right / gridSize).toInt() + 1) * gridSize
        val startY = (bounds.top / gridSize).toInt() * gridSize
        val endY = ((bounds.bottom / gridSize).toInt() + 1) * gridSize
        var x = startX
        while (x <= endX) { canvas.drawLine(x, bounds.top, x, bounds.bottom, gridPaint); x += gridSize }
        var y = startY
        while (y <= endY) { canvas.drawLine(bounds.left, y, bounds.right, y, gridPaint); y += gridSize }
    }

    private fun drawOverlays(canvas: Canvas) {
        val zoomPercent = (transformManager.currentScale * 100).toInt()
        canvas.drawText("$zoomPercent%", 20f, height - 20f, zoomTextPaint)
        if (transformManager.currentScale < 1f) drawCanvasBounds(canvas)
    }

    private fun drawCanvasBounds(canvas: Canvas) {
        val boundsPaint = Paint().apply {
            color = Color.BLUE; alpha = 100; style = Paint.Style.STROKE; strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
        canvas.drawRect(
            transformManager.getTransformedRect(RectF(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat())),
            boundsPaint
        )
    }

    private fun isOutOfBounds(x: Float, y: Float) =
        x < 0 || x > canvasWidth || y < 0 || y > canvasHeight

    private fun handleFling(velocityX: Float, velocityY: Float) {
        flingAnimator.cancel()
        flingAnimator.apply {
            setFloatValues(0f, 1f); duration = 1000; interpolator = DecelerateInterpolator()
            var lastValue = 0f
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                val delta = value - lastValue; lastValue = value
                transformManager.translate(velocityX * delta * 0.002f, velocityY * delta * 0.002f)
                transformManager.constrainTranslation(canvasWidth.toFloat(), canvasHeight.toFloat(), width.toFloat(), height.toFloat())
                invalidate()
            }
            start()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gesture listeners
    // ─────────────────────────────────────────────────────────────────────────

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            isCanvasPanning = false

            // Cancel any in-progress stroke to prevent reconnection when drawing resumes.
            // TODO (post-Sprint 2): Once undo is fixed for move/resize operations,
            // upgrade this to commit the partial stroke instead of discarding it
            // (matches Procreate/Fresco behavior — preserves accidental work as undoable).
            currentPath.reset()
            currentStylusPoints.clear()
            isDrawingWithStylus = false

            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            transformManager.setScale(transformManager.currentScale * detector.scaleFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            animateZoom(if (transformManager.currentScale < 2f) 2f else 1f, e.x, e.y)
            return true
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}