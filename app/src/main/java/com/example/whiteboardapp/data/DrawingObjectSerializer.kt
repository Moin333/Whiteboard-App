package com.example.whiteboardapp.data

import android.graphics.Paint
import com.example.whiteboardapp.data.db.DrawingObjectRealm
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.ShapeType
import com.example.whiteboardapp.model.TextObject
import org.json.JSONObject

/**
 * A singleton object responsible for serializing [DrawingObject] models into JSON strings
 * for database storage and deserializing them back into their original object form.
 * This acts as a bridge between the complex in-memory objects and the simple string format
 * required by [DrawingObjectRealm].
 */
object DrawingObjectSerializer {

    /**
     * Serializes any given [DrawingObject] into a JSON string.
     * It delegates to a specific serialization function based on the object's type.
     *
     * @param obj The [DrawingObject] to serialize.
     * @return A JSON formatted string representing the object.
     */
    fun serialize(obj: DrawingObject): String {
        return when (obj) {
            is DrawingObject.PathObject -> serializePath(obj)
            is DrawingObject.ShapeObject -> serializeShape(obj)
            is TextObject -> serializeText(obj)
        }
    }


    /**
     * Deserializes a [DrawingObjectRealm] database entity into a [DrawingObject] model.
     * It reads the object's type and JSON data to reconstruct the appropriate model.
     *
     * @param realmObj The database object to deserialize.
     * @return A fully formed [DrawingObject], or null if deserialization fails.
     */
    fun deserialize(realmObj: DrawingObjectRealm): DrawingObject? {
        return try {
            val json = JSONObject(realmObj.data)
            val type = realmObj.type
            when (type) {
                "path" -> deserializePath(json)
                "shape" -> deserializeShape(json)
                "text" -> deserializeText(json)
                else -> null
            }
        } catch (e: Exception) {
            // Log error
            null
        }
    }

    // region Private Serialization Methods
    private fun serializePath(path: DrawingObject.PathObject): String {
        return JSONObject().apply {
            put("id", path.id)
            put("pathData", PathSerializer.pathToString(path.path))
            put("color", path.paint.color)
            put("strokeWidth", path.paint.strokeWidth.toDouble())
        }.toString()
    }

    private fun serializeShape(shape: DrawingObject.ShapeObject): String {
        return JSONObject().apply {
            put("id", shape.id)
            put("shapeType", shape.shapeType.name)
            put("startX", shape.startX.toDouble())
            put("startY", shape.startY.toDouble())
            put("endX", shape.endX.toDouble())
            put("endY", shape.endY.toDouble())
            put("strokeColor", shape.paint.color)
            put("strokeWidth", shape.paint.strokeWidth.toDouble())
            put("rotation", shape.rotation.toDouble())
            shape.fillPaint?.let { put("fillColor", it.color) }
        }.toString()
    }

    private fun serializeText(text: TextObject): String {
        return JSONObject().apply {
            put("id", text.id)
            put("text", text.text)
            put("x", text.x.toDouble())
            put("y", text.y.toDouble())
            put("textSize", text.textSize.toDouble())
            put("textColor", text.textColor)
            put("isBold", text.isBold)
            put("isItalic", text.isItalic)
            put("alignment", text.alignment.name)
            put("rotation", text.rotation.toDouble())
        }.toString()
    }
    // endregion

    // region Private Deserialization Methods
    private fun deserializePath(json: JSONObject): DrawingObject.PathObject {
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            color = json.getInt("color")
            strokeWidth = json.getDouble("strokeWidth").toFloat()
        }
        return DrawingObject.PathObject(
            id = json.getString("id"),
            path = PathSerializer.stringToPath(json.getString("pathData")),
            paint = paint
        )
    }

    private fun deserializeShape(json: JSONObject): DrawingObject.ShapeObject {
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            color = json.getInt("strokeColor")
            strokeWidth = json.getDouble("strokeWidth").toFloat()
        }
        val fillPaint = if (json.has("fillColor")) Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = json.getInt("fillColor")
        } else null

        return DrawingObject.ShapeObject(
            id = json.getString("id"),
            shapeType = ShapeType.valueOf(json.getString("shapeType")),
            startX = json.getDouble("startX").toFloat(),
            startY = json.getDouble("startY").toFloat(),
            endX = json.getDouble("endX").toFloat(),
            endY = json.getDouble("endY").toFloat(),
            paint = strokePaint,
            fillPaint = fillPaint
        ).apply {
            rotation = json.optDouble("rotation", 0.0).toFloat()
        }
    }

    private fun deserializeText(json: JSONObject): TextObject {
        return TextObject(
            id = json.getString("id"),
            text = json.getString("text"),
            x = json.getDouble("x").toFloat(),
            y = json.getDouble("y").toFloat(),
            textSize = json.getDouble("textSize").toFloat(),
            textColor = json.getInt("textColor"),
            isBold = json.getBoolean("isBold"),
            isItalic = json.getBoolean("isItalic"),
            alignment = Paint.Align.valueOf(json.getString("alignment"))
        ).apply {
            rotation = json.optDouble("rotation", 0.0).toFloat()
        }
    }
    // endregion
}