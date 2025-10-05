package com.example.whiteboardapp.manager

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.whiteboardapp.model.DrawingObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale

/**
 * Manages the process of exporting the whiteboard canvas to various file formats.
 * All operations are performed on a background thread using coroutines.
 *
 * @param context The application context.
 */
class ExportManager(private val context: Context) {

    // Defines the available export file formats.
    enum class ExportFormat {
        PNG, JPEG, PDF
    }

    /**
     * A data class holding all configuration options for an export operation.
     *
     * @property format The target file format.
     * @property quality The compression quality for JPEG/PNG (0-100).
     * @property scale A multiplier for the output resolution (e.g., 2.0f for 2x size).
     * @property backgroundColor The background color of the exported file.
     * @property includeWatermark Whether to add a watermark to the output.
     * @property cropToContent If true, the output is cropped to the smallest rectangle containing all objects.
     */
    data class ExportOptions(
        val format: ExportFormat = ExportFormat.PNG,
        val quality: Int = 95,
        val scale: Float = 1.0f,
        val backgroundColor: Int = Color.WHITE,
        val includeWatermark: Boolean = false,
        val cropToContent: Boolean = false,
    )

    /**
     * The main export function. It runs on the IO dispatcher and delegates to format-specific handlers.
     *
     * @return A [Result] containing the output [File] on success or an [Exception] on failure.
     */
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

    // Handles the logic for exporting to a PNG file.
    private fun exportAsPNG(
        objects: List<DrawingObject>,
        width: Int,
        height: Int,
        options: ExportOptions,
        outputFile: File,
    ) {
        // Determine the drawing bounds (either full canvas or cropped to content).
        val bounds = if (options.cropToContent) {
            calculateContentBounds(objects)
        } else {
            RectF(0f, 0f, width.toFloat(), height.toFloat())
        }

        // // Create a bitmap with the specified dimensions and scale.
        val exportWidth = (bounds.width() * options.scale).toInt()
        val exportHeight = (bounds.height() * options.scale).toInt()
        val bitmap = createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Prepare the canvas (scale, translate for cropping, set background).
        canvas.scale(options.scale, options.scale)
        canvas.translate(-bounds.left, -bounds.top)
        canvas.drawColor(options.backgroundColor)

        // Draw all objects onto the bitmap canvas.
        objects.forEach { it.draw(canvas) }

        if (options.includeWatermark) {
            canvas.withTranslation(bounds.left, bounds.top) {
                drawWatermark(this, exportWidth, exportHeight, options.scale)
            }
        }

        outputFile.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, options.quality, stream)
        }
        bitmap.recycle()
    }

    // Handles the logic for exporting to a JPEG file.
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
            canvas.withTranslation(bounds.left, bounds.top) {
                drawWatermark(this, exportWidth, exportHeight, options.scale)
            }
        }

        outputFile.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, options.quality, stream)
        }
        bitmap.recycle()
    }

    // Handles the logic for exporting to a PDF.
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
            canvas.withSave {

                // Reset translation to draw watermark at correct position
                if (options.cropToContent) {
                    translate(bounds.left, bounds.top)
                }

                drawWatermarkPDF(this, pdfWidth, pdfHeight)
            }
        }

        pdfDocument.finishPage(page)

        outputFile.outputStream().use { stream ->
            pdfDocument.writeTo(stream)
        }

        pdfDocument.close()
    }

    // Calculates the minimum bounding box that contains all drawing objects, plus padding.
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

        canvas.withScale(1f / scale, 1f / scale) {
            drawText(
                text,
                (width * scale) - textBounds.width() - 20f,
                (height * scale) - 20f,
                watermarkPaint
            )
        }
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