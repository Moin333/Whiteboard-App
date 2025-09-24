package com.example.whiteboardapp.model

sealed class DrawingTool {
    object Pen : DrawingTool()
    object Eraser : DrawingTool()
    data class Shape(val type: ShapeType) : DrawingTool()
    object Text : DrawingTool()
    object Select : DrawingTool()
}

enum class ShapeType {
    LINE, RECTANGLE, CIRCLE, POLYGON
}