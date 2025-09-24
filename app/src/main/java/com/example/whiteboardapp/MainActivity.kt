package com.example.whiteboardapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.whiteboardapp.model.DrawingTool
import com.example.whiteboardapp.view.WhiteboardView
import com.example.whiteboardapp.viewmodel.WhiteboardViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: WhiteboardViewModel by viewModels()
    private lateinit var whiteboardView: WhiteboardView
    private lateinit var colorPalette: LinearLayout
    private lateinit var strokeWidthSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        whiteboardView = findViewById(R.id.whiteboardView)
        whiteboardView.setViewModel(viewModel)

        setupToolbar()
    }

    private fun setupToolbar() {
        // Tool selection buttons
        findViewById<ImageButton>(R.id.btnPen).setOnClickListener { viewModel.selectTool(DrawingTool.Pen) }
        findViewById<ImageButton>(R.id.btnEraser).setOnClickListener { viewModel.selectTool(DrawingTool.Eraser) }

        // Color Palette
        colorPalette = findViewById(R.id.colorPalette)
        setupColorPalette()

        // Stroke Width Spinner
        strokeWidthSpinner = findViewById(R.id.strokeWidthSpinner)
        setupStrokeWidthSpinner()
    }

    private fun setupColorPalette() {
        val colors = listOf(Color.BLACK, Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA)

        for (color in colors) {
            val colorView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(96, 96).also {
                    it.setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(color)
                setOnClickListener {
                    viewModel.setStrokeColor(color)
                }
            }
            colorPalette.addView(colorView)
        }
    }

    private fun setupStrokeWidthSpinner() {
        val strokeWidths = listOf(5f, 10f, 15f, 20f, 30f)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, strokeWidths.map { "${it.toInt()}px" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        strokeWidthSpinner.adapter = adapter

        strokeWidthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setStrokeWidth(strokeWidths[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}