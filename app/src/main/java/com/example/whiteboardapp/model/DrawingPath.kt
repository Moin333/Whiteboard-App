package com.example.whiteboardapp.model

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.util.UUID

data class DrawingPath(
    val path: Path,
    val paint: Paint,
    val strokeWidth: Float = 5f,
    val color: Int = Color.BLACK,
    val id: String = UUID.randomUUID().toString()
)