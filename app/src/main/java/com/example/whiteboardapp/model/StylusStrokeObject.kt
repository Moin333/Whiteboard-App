package com.example.whiteboardapp.model

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.withRotation
import com.example.whiteboardapp.renderer.VariableWidthStrokeRenderer
import java.util.UUID

/**
 * A [DrawingObject] that stores a pressure-sensitive stylus stroke.
 *
 * Rendering strategy:
 *  - Stores raw [StrokePoint] list (immutable) plus a mutable translation offset for moves.
 *  - Lazily builds and caches a filled [Path] via [VariableWidthStrokeRenderer].
 *  - Cache is invalidated whenever the object is moved (offset changes).
 *  - Drawn with [Paint.Style.FILL] (no stroke) for sharp, anti-aliased edges.
 *
 * @param rawPoints      The sampled input points, in canvas-space coordinates.
 * @param baseWidth      The maximum stroke width (at pressure = 1.0), in canvas pixels.
 * @param color          ARGB color of the stroke.
 * @param isTiltEnabled  Whether to apply calligraphic tilt-based width asymmetry.
 */
class StylusStrokeObject(
    override val id: String = UUID.randomUUID().toString(),
    private val rawPoints: List<StrokePoint>,
    val baseWidth: Float,
    val color: Int,
    val isTiltEnabled: Boolean = true
) : DrawingObject() {

    // ── Movement offset ──────────────────────────────────────────────────────
    // We keep rawPoints immutable for efficient cloning and serialization.
    // Moves accumulate here and are applied when the cache is rebuilt.
    private var offsetX = 0f
    private var offsetY = 0f

    // ── Render cache ─────────────────────────────────────────────────────────
    private var cachedPath: Path? = null
    private var cachedBounds: RectF? = null

    private val fillPaint = Paint().apply {
        this.color = this@StylusStrokeObject.color
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // ── Public accessors ─────────────────────────────────────────────────────

    /**
     * Returns the stroke points with the accumulated move offset applied.
     * Used for rendering and serialization.
     */
    val points: List<StrokePoint>
        get() = if (offsetX == 0f && offsetY == 0f) {
            rawPoints
        } else {
            rawPoints.map { it.copy(x = it.x + offsetX, y = it.y + offsetY) }
        }

    override val bounds: RectF
        get() = cachedBounds ?: buildPathAndUpdateCache().second

    // ── DrawingObject implementation ──────────────────────────────────────────

    override fun draw(canvas: Canvas) {
        val (path, bounds) = if (cachedPath != null && cachedBounds != null) {
            cachedPath!! to cachedBounds!!
        } else {
            buildPathAndUpdateCache()
        }

        canvas.withRotation(rotation, bounds.centerX(), bounds.centerY()) {
            drawPath(path, fillPaint)
        }
    }

    override fun contains(x: Float, y: Float): Boolean {
        val b = bounds
        return if (rotation != 0f) {
            val rotated = rotatePoint(x, y, b.centerX(), b.centerY(), -rotation)
            b.contains(rotated.x, rotated.y)
        } else {
            b.contains(x, y)
        }
    }

    override fun move(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
        invalidateCache()
    }

    override fun clone(): DrawingObject {
        // Bake the offset into a new rawPoints list so the clone is standalone.
        return StylusStrokeObject(
            id = UUID.randomUUID().toString(),
            rawPoints = points.toList(), // applies offset
            baseWidth = baseWidth,
            color = color,
            isTiltEnabled = isTiltEnabled
        ).also { it.rotation = this.rotation }
    }

    // ── Cache management ─────────────────────────────────────────────────────

    private fun buildPathAndUpdateCache(): Pair<Path, RectF> {
        val pts = points
        val path = VariableWidthStrokeRenderer.buildStrokePath(
            points = pts,
            baseWidth = baseWidth,
            isTiltEnabled = isTiltEnabled
        )

        // Compute bounds from the rendered path, then add a margin equal to
        // half the base width so selection hit-testing has some tolerance.
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val margin = (baseWidth / 2f).coerceAtLeast(4f)
        bounds.inset(-margin, -margin)

        cachedPath = path
        cachedBounds = bounds
        return path to bounds
    }

    private fun invalidateCache() {
        cachedPath = null
        cachedBounds = null
    }

    // ── Serialization helper ─────────────────────────────────────────────────

    /**
     * Returns the stroke's points with offset fully applied.
     * Call this from [DrawingObjectSerializer] when persisting.
     */
    fun getSerializablePoints(): List<StrokePoint> = points
}