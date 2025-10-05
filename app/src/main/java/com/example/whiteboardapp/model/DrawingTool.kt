package com.example.whiteboardapp.model

/**
 * A sealed class representing the various tools available in the whiteboard toolbar.
 * Using a sealed class allows for exhaustive `when` statements, ensuring all tools are handled.
 */
sealed class DrawingTool {
    // The freehand drawing tool.
    object Pen : DrawingTool()
    // The tool for erasing objects.
    object Eraser : DrawingTool()
    // The tool for drawing predefined shapes. It holds the [ShapeType].
    data class Shape(val type: ShapeType) : DrawingTool()
    // The tool for adding and editing text.
    object Text : DrawingTool()
    // The tool for selecting, moving, resizing, and rotating objects.
    object Select : DrawingTool()
}

/**
 * An enum defining the types of shapes that can be drawn.
 */
enum class ShapeType {
    LINE, RECTANGLE, CIRCLE, POLYGON
}