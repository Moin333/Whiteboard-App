package com.example.whiteboardapp.model

/**
 * Represents a single sampled point along a stylus or touch stroke.
 *
 * @property x          Canvas-space X coordinate (post-transform from screen space).
 * @property y          Canvas-space Y coordinate.
 * @property pressure   Normalized pressure in [0.0, 1.0]. 1.0 = maximum reported pressure.
 *                      For finger input with no pressure sensor, defaults to 1.0.
 * @property tiltX      X-component of the stylus tilt vector (derived from AXIS_TILT +
 *                      AXIS_ORIENTATION). Range roughly [-1, 1].
 * @property tiltY      Y-component of the stylus tilt vector.
 * @property timestamp  Event timestamp in milliseconds (from MotionEvent.eventTime).
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val timestamp: Long = 0L
) {
    companion object {
        /** Minimum pressure floor — prevents zero-width invisible segments. */
        const val MIN_PRESSURE = 0.05f

        /** Creates a plain point with default pressure (used for finger/fallback input). */
        fun plain(x: Float, y: Float, timestamp: Long = 0L) =
            StrokePoint(x = x, y = y, pressure = 1.0f, timestamp = timestamp)
    }
}