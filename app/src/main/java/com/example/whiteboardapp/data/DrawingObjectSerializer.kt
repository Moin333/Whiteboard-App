package com.example.whiteboardapp.data

import android.graphics.Paint
import com.example.whiteboardapp.data.db.DrawingObjectRealm
import com.example.whiteboardapp.model.DrawingObject
import com.example.whiteboardapp.model.ShapeType
import com.example.whiteboardapp.model.StylusStrokeObject
import com.example.whiteboardapp.model.StrokePoint
import com.example.whiteboardapp.model.TextObject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes [DrawingObject] models into JSON strings for Realm storage,
 * and deserializes them back.
 *
 * Supported types and their "type" field values in the DB:
 *  - [DrawingObject.PathObject]  → "path"
 *  - [DrawingObject.ShapeObject] → "shape"
 *  - [TextObject]                → "text"
 *  - [StylusStrokeObject]        → "stylus_stroke"
 */
object DrawingObjectSerializer {

    fun serialize(obj: DrawingObject): String {
        return when (obj) {
            is DrawingObject.PathObject  -> serializePath(obj)
            is DrawingObject.ShapeObject -> serializeShape(obj)
            is TextObject                -> serializeText(obj)
            is StylusStrokeObject        -> serializeStylusStroke(obj)
        }
    }

    fun deserialize(realmObj: DrawingObjectRealm): DrawingObject? {
        return try {
            val json = JSONObject(realmObj.data)
            when (realmObj.type) {
                "path"          -> deserializePath(json)
                "shape"         -> deserializeShape(json)
                "text"          -> deserializeText(json)
                "stylus_stroke" -> deserializeStylusStroke(json)
                else            -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

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

    private fun serializeStylusStroke(obj: StylusStrokeObject): String {
        // Use getSerializablePoints() to ensure move offsets are baked in
        val pointsArray = JSONArray()
        obj.getSerializablePoints().forEach { p ->
            pointsArray.put(JSONObject().apply {
                put("x", p.x.toDouble())
                put("y", p.y.toDouble())
                put("pressure", p.pressure.toDouble())
                put("tiltX", p.tiltX.toDouble())
                put("tiltY", p.tiltY.toDouble())
                put("ts", p.timestamp)
            })
        }
        return JSONObject().apply {
            put("id", obj.id)
            put("baseWidth", obj.baseWidth.toDouble())
            put("color", obj.color)
            put("isTiltEnabled", obj.isTiltEnabled)
            put("rotation", obj.rotation.toDouble())
            put("points", pointsArray)
        }.toString()
    }

    // ── Deserialization ───────────────────────────────────────────────────────

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

    private fun deserializeStylusStroke(json: JSONObject): StylusStrokeObject {
        val pointsArray = json.getJSONArray("points")
        val points = ArrayList<StrokePoint>(pointsArray.length())
        for (i in 0 until pointsArray.length()) {
            val pJson = pointsArray.getJSONObject(i)
            points.add(
                StrokePoint(
                    x = pJson.getDouble("x").toFloat(),
                    y = pJson.getDouble("y").toFloat(),
                    pressure = pJson.getDouble("pressure").toFloat(),
                    tiltX = pJson.optDouble("tiltX", 0.0).toFloat(),
                    tiltY = pJson.optDouble("tiltY", 0.0).toFloat(),
                    timestamp = pJson.optLong("ts", 0L)
                )
            )
        }
        return StylusStrokeObject(
            id = json.getString("id"),
            rawPoints = points,
            baseWidth = json.getDouble("baseWidth").toFloat(),
            color = json.getInt("color"),
            isTiltEnabled = json.optBoolean("isTiltEnabled", true)
        ).apply {
            rotation = json.optDouble("rotation", 0.0).toFloat()
        }
    }
}