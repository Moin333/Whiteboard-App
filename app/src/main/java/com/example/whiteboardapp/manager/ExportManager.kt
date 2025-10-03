package com.example.whiteboardapp.manager

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.whiteboardapp.model.DrawingObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.graphics.createBitmap

class ExportManager(private val context: Context) {

    enum class ExportFormat {
        PNG, JPEG, PDF
    }

    data class ExportOptions(
        val format: ExportFormat = ExportFormat.PNG,
        val quality: Int = 95,
        val scale: Float = 1.0f,
        val backgroundColor: Int = Color.WHITE,
        val includeWatermark: Boolean = false,
        val cropToContent: Boolean = false,
    )

    suspend fun exportCanvas(
        objects: List<DrawingObject>,
        canvasWidth: Int,
        canvasHeight: Int,
        options: ExportOptions,
        outputFile: File,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            when (options.format) {
                ExportFormat.PNG -> exportAsPNG(objects, canvasWidth, canvasHeight, options, outputFile)
                ExportFormat.JPEG -> exportAsJPEG(objects, canvasWidth, canvasHeight, options, outputFile)
                ExportFormat.PDF -> exportAsPDF(objects, canvasWidth, canvasHeight, options, outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun exportAsPNG(
        objects: List<DrawingObject>,
        width: Int,
        height: Int,
        options: ExportOptions,
        outputFile: File,
    ) {
        val bounds = if (options.cropToContent) {
            calculateContentBounds(objects)
        } else {
            RectF(0f, 0f, width.toFloat(), height.toFloat())
        }

        val exportWidth = (bounds.width() * options.scale).toInt()
        val exportHeight = (bounds.height() * options.scale).toInt()

        val bitmap = createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.scale(options.scale, options.scale)
        canvas.translate(-bounds.left, -bounds.top)
        canvas.drawColor(options.backgroundColor)

        objects.forEach { it.draw(canvas) }

        if (options.includeWatermark) {
            canvas.save()
            canvas.translate(bounds.left, bounds.top)
            drawWatermark(canvas, exportWidth, exportHeight, options.scale)
            canvas.restore()
        }

        outputFile.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, options.quality, stream)
        }

        bitmap.recycle()
    }

    private fun exportAsJPEG(
        objects: List<DrawingObject>,
        width: Int,
        height: Int,
        options: ExportOptions,
        outputFile: File,
    ) {
        val bounds = if (options.cropToContent) {
            calculateContentBounds(objects)
        } else {
            RectF(0f, 0f, width.toFloat(), height.toFloat())
        }

        val exportWidth = (bounds.width() * options.scale).toInt()
        val exportHeight = (bounds.height() * options.scale).toInt()

        val bitmap = createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.scale(options.scale, options.scale)
        canvas.translate(-bounds.left, -bounds.top)
        canvas.drawColor(options.backgroundColor)

        objects.forEach { it.draw(canvas) }

        if (options.includeWatermark) {
            canvas.save()
            canvas.translate(bounds.left, bounds.top)
            drawWatermark(canvas, exportWidth, exportHeight, options.scale)
            canvas.restore()
        }

        outputFile.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, options.quality, stream)
        }

        bitmap.recycle()
    }

    private fun exportAsPDF(
        objects: List<DrawingObject>,
        width: Int,
        height: Int,
        options: ExportOptions,
        outputFile: File,
    ) {
        val bounds = if (options.cropToContent) {
            calculateContentBounds(objects)
        } else {
            RectF(0f, 0f, width.toFloat(), height.toFloat())
        }

        // PDF page size (in points, 72 points = 1 inch)
        val pdfWidth = bounds.width().toInt()
        val pdfHeight = bounds.height().toInt()

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas

        // Apply transformation if cropping
        if (options.cropToContent) {
            canvas.translate(-bounds.left, -bounds.top)
        }

        canvas.drawColor(options.backgroundColor)

        // Draw all objects
        objects.forEach { it.draw(canvas) }

        // Draw watermark at the bottom right of the PDF page
        if (options.includeWatermark) {
            canvas.save()

            // Reset translation to draw watermark at correct position
            if (options.cropToContent) {
                canvas.translate(bounds.left, bounds.top)
            }

            drawWatermarkPDF(canvas, pdfWidth, pdfHeight)
            canvas.restore()
        }

        pdfDocument.finishPage(page)

        outputFile.outputStream().use { stream ->
            pdfDocument.writeTo(stream)
        }

        pdfDocument.close()
    }

    private fun calculateContentBounds(objects: List<DrawingObject>): RectF {
        if (objects.isEmpty()) return RectF(0f, 0f, 100f, 100f)

        val bounds = RectF(Float.MAX_VALUE, Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        objects.forEach { obj ->
            val objBounds = obj.bounds
            bounds.left = minOf(bounds.left, objBounds.left)
            bounds.top = minOf(bounds.top, objBounds.top)
            bounds.right = maxOf(bounds.right, objBounds.right)
            bounds.bottom = maxOf(bounds.bottom, objBounds.bottom)
        }

        val padding = 20f
        bounds.inset(-padding, -padding)

        return bounds
    }

    private fun drawWatermark(canvas: Canvas, width: Int, height: Int, scale: Float) {
        val watermarkPaint = Paint().apply {
            textSize = 24f / scale
            color = Color.GRAY
            alpha = 100
            isAntiAlias = true
        }

        val text = "Whiteboard by TATA EDGE CLASS"
        val textBounds = Rect()
        watermarkPaint.getTextBounds(text, 0, text.length, textBounds)

        canvas.save()
        canvas.scale(1f / scale, 1f / scale)
        canvas.drawText(
            text,
            (width * scale) - textBounds.width() - 20f,
            (height * scale) - 20f,
            watermarkPaint
        )
        canvas.restore()
    }

    private fun drawWatermarkPDF(canvas: Canvas, width: Int, height: Int) {
        val watermarkPaint = Paint().apply {
            textSize = 20f
            color = Color.GRAY
            alpha = 120
            isAntiAlias = true
        }

        val text = "Whiteboard by TATA EDGE CLASS"
        val textBounds = Rect()
        watermarkPaint.getTextBounds(text, 0, text.length, textBounds)

        // Draw at bottom right
        canvas.drawText(
            text,
            width - textBounds.width() - 20f,
            height - 20f,
            watermarkPaint
        )
    }
}